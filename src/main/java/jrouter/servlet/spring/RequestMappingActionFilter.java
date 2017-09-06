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
package jrouter.servlet.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import jrouter.ActionFilter;
import jrouter.annotation.Action;
import jrouter.annotation.Parameter;
import jrouter.annotation.Result;
import jrouter.annotation.Scope;
import jrouter.util.CollectionUtil;
import jrouter.util.StringUtil;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 适配{@link RequestMapping}的{@code ActionFilter}实现。
 */
public class RequestMappingActionFilter implements ActionFilter {

    @Override
    public boolean accept(Method method) {
        return method.isAnnotationPresent(RequestMapping.class);
    }

    @Override
    public Action getAnnotation(Method method) {
        Action action = method.getAnnotation(Action.class);
        if (action != null) {
            return action;
        }
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        if (mapping != null) {
            String[] values = mapping.value();
            if (CollectionUtil.isEmpty(values)) {
                values = mapping.path();
            }
            if (CollectionUtil.isEmpty(values)) {
                String name = mapping.name();
                if (StringUtil.isNotBlank(name)) {
                    values = new String[]{name};
                }
            }
            if (CollectionUtil.isEmpty(values)) {
                values = new String[]{method.getName()};
            }

            final String[] paths = values;
            return new Action() {
                @Override
                public String[] value() {
                    return paths;
                }

                @Override
                public String[] name() {
                    return paths;
                }

                @Override
                public String interceptorStack() {
                    return "";
                }

                @Override
                public String[] interceptors() {
                    return CollectionUtil.EMPTY_STRING_ARRAY;
                }

                @Override
                public Result[] results() {
                    return new Result[0];
                }

                @Override
                public Scope scope() {
                    return Scope.SINGLETON;
                }

                @Override
                public Parameter[] parameters() {
                    return new Parameter[0];
                }

                @Override
                public Class<? extends Annotation> annotationType() {
                    return RequestMapping.class;
                }
            };
        }
        return null;
    }
}