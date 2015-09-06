/*
 * Copyright (C) 2010-2111 sunjumper@163.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package jrouter.servlet;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import jrouter.ActionFactory;
import jrouter.ActionInvocation;
import jrouter.ActionProxy;
import jrouter.JRouterException;
import jrouter.ParameterConverter;
import jrouter.annotation.Result;
import jrouter.impl.DefaultActionFactory;

/**
 * ServletActionFactory invoke Action with Http parameters.
 */
public interface ServletActionFactory extends ActionFactory {

    /**
     * Use this instead of invokeAction(java.lang.String, java.lang.Object...) to pass Http parameters.
     *
     * @param <T> Generic type.
     * @param path Action path.
     * @param request HttpServletRequest.
     * @param response HttpServletResponse.
     * @param servletContext ServletContext.
     *
     * @return Action invoked result.
     *
     * @throws JRouterException if error occurred.
     *
     * @see #invokeAction(java.lang.String, java.lang.Object...)
     */
    <T> T invokeAction(String path, HttpServletRequest request, HttpServletResponse response,
            ServletContext servletContext) throws JRouterException;

    /**
     * 提供ServletActionFactory接口默认实现。覆写createActionInvocation方法创建ServletActionInvocation接口对象。
     *
     * @see #createActionInvocation(java.lang.String, java.lang.Object...)
     */
    public static class DefaultServletActionFactory extends DefaultActionFactory implements
            ServletActionFactory {

        /** Use ThreadLocal to store Http parameter object or not */
        private final boolean useThreadLocal = true;

        /* Action path是否大小写敏感，默认区分大小写 **/
        private boolean actionPathCaseSensitive = true;

        /**
         * 根据指定的键值映射构造初始化数据的ServletActionFactory对象。
         *
         * @param properties 指定的初始化数据键值映射。
         *
         * @see DefaultActionFactory
         */
        public DefaultServletActionFactory(Map<String, Object> properties) {
            super(properties);
            Object val = properties.get("actionPathCaseSensitive");
            if (val != null)
                actionPathCaseSensitive = Boolean.valueOf(val.toString());
        }

        @Override
        public <T> T invokeAction(String path, HttpServletRequest request,
                HttpServletResponse response, ServletContext sc) throws JRouterException {
            //invoke and pass http parameters
            return (T) super.invokeAction(actionPathCaseSensitive ? path : path.toLowerCase(),
                    request, response, sc);
        }

        /**
         * 创建并返回ServletActionInvocation接口对象。
         *
         * @return ServletActionInvocation接口对象。
         *
         * @see ServletActionInvocation
         */
        @Override
        protected ActionInvocation<?> createActionInvocation(String path, Object... params) {
            //create no parameters ActionInvocation
            ActionInvocation<?> invocation = super.createActionInvocation(path);
            DefaultServletActionInvocation servletInvocation = null;

            //优先从invokeAction参数中获取Http参数对象，已由invokeAction方法指定参数顺序
            if (checkHttpParameters(params)) {
                servletInvocation = new DefaultServletActionInvocation(invocation,
                        (HttpServletRequest) params[0],
                        (HttpServletResponse) params[1],
                        (ServletContext) params[2],
                        ServletThreadContext.getContextMap());
            }
            //use ThreadLocal
            if (servletInvocation == null && useThreadLocal) {
                servletInvocation = new DefaultServletActionInvocation(invocation,
                        ServletThreadContext.getRequest(),
                        ServletThreadContext.getResponse(),
                        ServletThreadContext.getServletContext(),
                        ServletThreadContext.getContextMap());
            }

            //store in ServletThreadContext ThreadLocal if needed
            if (useThreadLocal) {
                ServletThreadContext.setActionInvocation(servletInvocation);
            }
            if (servletInvocation != null) {
                //pass DefaultServletActionInvocation as parameter to the converter if needed
                invocation.setParameterConverter(invocation.getActionFactory().getConverterFactory().getParameterConverter(servletInvocation));
                //重设调用ActionInvocation的转换参数
                invocation.setConvertParameters(
                        new Object[]{
                            servletInvocation.getRequest(),
                            servletInvocation.getResponse(),
                            servletInvocation.getServletContext(),
                            servletInvocation});
                return servletInvocation;
            } else {
                //retrun ActionInvocation if can't get any http parameters and null DefaultServletActionInvocation
                return invocation;
            }
        }

        @Override
        protected String buildActionPath(String namespace, String aname, Method method) {
            String path = super.buildActionPath(namespace, aname, method);
            return actionPathCaseSensitive ? path : path.toLowerCase();
        }

        /**
         * 检测invokeAction方法传递过来参数的正确性。
         *
         * @param params 由invokeAction方法传递过来参数。
         *
         * @return 参数是否为正确的Http Servlet对象。
         *
         * @see #invokeAction(java.lang.String, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, javax.servlet.ServletContext)
         */
        private boolean checkHttpParameters(Object... params) {
            return params != null && params.length == 3
                    && (params[0] instanceof HttpServletRequest)
                    && (params[1] instanceof HttpServletResponse)
                    && (params[2] instanceof ServletContext);
        }

        /**
         * Action路径是否大小写敏感，默认区分大小写。
         *
         * @return Action路径是否大小写敏感，默认区分大小写。
         */
        public boolean isActionPathCaseSensitive() {
            return actionPathCaseSensitive;
        }

    }

    /**
     * 扩展ActionInvocation，提供获取Http参数对象，并提供给参数转换器。
     */
    public static class DefaultServletActionInvocation implements ServletActionInvocation {

        /* 代理的ActionInvocation */
        private final ActionInvocation invocation;

        /** Http request */
        private final HttpServletRequest request;

        /** Http response */
        private final HttpServletResponse response;

        /** Http request parameters map */
        private final RequestMap requestMap;

        /** ServletContext */
        private final ServletContext servletContext;

        /** Store key-value */
        private final Map<String, Object> contextMap;

        public DefaultServletActionInvocation(ActionInvocation invocation,
                HttpServletRequest request, HttpServletResponse response,
                ServletContext servletContext, Map<String, Object> contextMap) {
            this.invocation = invocation;
            this.request = request;
            this.requestMap = new RequestMap(request);
            this.response = response;
            this.servletContext = servletContext;
            this.contextMap = contextMap;
        }

        @Override
        public ActionFactory getActionFactory() {
            return invocation.getActionFactory();
        }

        @Override
        public ActionProxy getActionProxy() {
            return invocation.getActionProxy();
        }

        @Override
        public boolean isExecuted() {
            return invocation.isExecuted();
        }

        @Override
        public Object[] getParameters() {
            return invocation.getParameters();
        }

        @Override
        public Object invoke(Object... params) throws JRouterException {
            return invocation.invoke(params);
        }

        @Override
        public Object invokeActionOnly(Object... params) throws JRouterException {
            return invocation.invokeActionOnly(params);
        }

        @Override
        public Object getInvokeResult() {
            return invocation.getInvokeResult();
        }

        @Override
        public void setInvokeResult(Object result) {
            invocation.setInvokeResult(result);
        }

        @Override
        public void setResult(Result result) {
            invocation.setResult(result);
        }

        @Override
        public Result getResult() {
            return invocation.getResult();
        }

        @Override
        public void setParameterConverter(ParameterConverter parameterConverter) {
            invocation.setParameterConverter(parameterConverter);
        }

        @Override
        public ParameterConverter getParameterConverter() {
            return invocation.getParameterConverter();
        }

        @Override
        public void setConvertParameters(Object... params) {
            invocation.setConvertParameters(params);
        }

        @Override
        public Object[] getConvertParameters() {
            return invocation.getConvertParameters();
        }

        @Override
        public HttpServletRequest getRequest() {
            return this.request;
        }

        @Override
        public HttpSession getSession() {
            return getRequest().getSession();
        }

        @Override
        public Map<String, String[]> getRequestParameters() {
            return this.requestMap;
        }

        @Override
        public HttpServletResponse getResponse() {
            return this.response;
        }

        @Override
        public ServletContext getServletContext() {
            return this.servletContext;
        }

        @Override
        public Map<String, Object> getContextMap() {
            return contextMap;
        }

    }
}
