/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.language.csimple;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExtendedCamelContext;

/**
 * Base class for source code generateed csimple expressions.
 */
public abstract class CSimpleSupport implements CSimpleExpression, CSimpleMethod {

    private ExtendedCamelContext camelContext;

    @Override
    public void init(CamelContext context) {
        this.camelContext = context.adapt(ExtendedCamelContext.class);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        Object body = exchange.getIn().getBody();
        Object out;
        try {
            out = evaluate(exchange.getContext(), exchange, exchange.getIn(), body);
        } catch (Exception e) {
            throw new ExpressionEvaluationException(this, exchange, e);
        }
        return camelContext.getTypeConverter().convertTo(type, exchange, out);
    }

    @Override
    public boolean matches(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        Object out;
        try {
            out = evaluate(exchange.getContext(), exchange, exchange.getIn(), body);
        } catch (Exception e) {
            throw new ExpressionEvaluationException(this, exchange, e);
        }
        return camelContext.getTypeConverter().convertTo(boolean.class, exchange, out);
    }

}
