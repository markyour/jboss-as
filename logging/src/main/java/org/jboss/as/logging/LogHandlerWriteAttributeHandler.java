/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.logging;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceRegistry;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.logging.CommonAttributes.ENCODING;
import static org.jboss.as.logging.CommonAttributes.FILTER;
import static org.jboss.as.logging.CommonAttributes.FORMATTER;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.LoggingMessages.MESSAGES;

/**
 * Date: 12.10.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class LogHandlerWriteAttributeHandler<T extends Handler> extends AbstractWriteAttributeHandler<T> {
    private final Map<String, AttributeDefinition> attributes;

    LogHandlerWriteAttributeHandler(final AttributeDefinition... attributes) {
        this.attributes = new HashMap<String, AttributeDefinition>();
        this.attributes.put(LEVEL.getName(), LEVEL);
        this.attributes.put(FILTER.getName(), FILTER);
        this.attributes.put(FORMATTER.getName(), FORMATTER);
        this.attributes.put(ENCODING.getName(), ENCODING);
        for (AttributeDefinition attr : attributes) {
            this.attributes.put(attr.getName(), attr);
        }
    }

    @Override
    protected final boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue, final HandbackHolder<T> handbackHolder) throws OperationFailedException {
        final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
        final String name = address.getLastElement().getValue();
        final ServiceRegistry serviceRegistry = context.getServiceRegistry(false);
        final ServiceController<Handler> controller = (ServiceController<Handler>) serviceRegistry.getService(LogServices.handlerName(name));
        if (controller == null) {
            return false;
        }
        // Attempt to cast handler
        final T handler = (T) controller.getValue();
        if (LEVEL.getName().equals(attributeName)) {
            handler.setLevel(Level.parse(LEVEL.validateResolvedOperation(operation).asString()));
        } else if (FILTER.getName().equals(attributeName)) {
            // TODO (jrp) implement filter
        } else if (FORMATTER.getName().equals(attributeName)) {
            AbstractFormatterSpec.fromModelNode(FORMATTER.validateResolvedOperation(operation)).apply(handler);
        } else if (ENCODING.getName().equals(attributeName)) {
            try {
                handler.setEncoding(ENCODING.validateResolvedOperation(operation).asString());
            } catch (UnsupportedEncodingException e) {
                throw new OperationFailedException(e, new ModelNode().set(MESSAGES.failedToSetHandlerEncoding()));
            }
        }
        return applyUpdateToRuntime(operation, attributeName, resolvedValue, currentValue, handler);
    }

    @Override
    protected final void revertUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode valueToRestore, final ModelNode valueToRevert, final T handler) throws OperationFailedException {
        if (handler != null) {
            if (LEVEL.getName().equals(attributeName)) {
                handler.setLevel(Level.parse(LEVEL.validateResolvedOperation(valueToRestore).asString()));
            } else if (FILTER.getName().equals(attributeName)) {
                // TODO (jrp) implement filter
            } else if (FORMATTER.getName().equals(attributeName)) {
                AbstractFormatterSpec.fromModelNode(FORMATTER.validateResolvedOperation(valueToRestore)).apply(handler);
            } else if (ENCODING.getName().equals(attributeName)) {
                try {
                    handler.setEncoding(ENCODING.validateResolvedOperation(valueToRestore).asString());
                } catch (UnsupportedEncodingException e) {
                    throw new OperationFailedException(e, new ModelNode().set(MESSAGES.failedToSetHandlerEncoding()));
                }
            }
            revertUpdateToRuntime(operation, attributeName, valueToRestore, valueToRevert, handler);
        }
    }

    /**
     * Applies additional runtime attributes for the handler.
     *
     * @param operation     the operation
     * @param attributeName the name of the attribute being modified
     * @param resolvedValue the new value for the attribute, after {@link ModelNode#resolve()} has been called on it
     * @param currentValue  the existing value for the attribute
     * @param handler       the {@link Handler handler} to apply the changes to.
     *
     * @return {@code true} if the server requires restart to effect the attribute value change; {@code false} if not.
     *
     * @throws OperationFailedException if the operation fails.
     */
    protected abstract boolean applyUpdateToRuntime(final ModelNode operation, final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue, final T handler) throws OperationFailedException;

    /**
     * Reverts updates to the handler.
     *
     * @param operation      the operation
     * @param attributeName  the name of the attribute being modified
     * @param valueToRestore the previous value for the attribute, before this operation was executed
     * @param valueToRevert  the new value for the attribute that should be reverted
     * @param handler        the handler to apply the changes to.
     *
     * @throws OperationFailedException if the operation fails.
     */
    protected abstract void revertUpdateToRuntime(final ModelNode operation, final String attributeName, final ModelNode valueToRestore, final ModelNode valueToRevert, final T handler) throws OperationFailedException;

    @Override
    protected final void validateResolvedValue(final String name, final ModelNode value) throws OperationFailedException {
        if (attributes.containsKey(name)) {
            attributes.get(name).getValidator().validateResolvedParameter(name, value);
        } else {
            super.validateResolvedValue(name, value);
        }
    }

    @Override
    protected final void validateUnresolvedValue(final String name, final ModelNode value) throws OperationFailedException {
        if (attributes.containsKey(name)) {
            attributes.get(name).getValidator().validateParameter(name, value);
        } else {
            super.validateUnresolvedValue(name, value);
        }
    }

    /**
     * Returns a collection of attributes used for the write attribute.
     *
     * @return a collection of attributes.
     */
    public final Collection<AttributeDefinition> getAttributes() {
        return Collections.unmodifiableCollection(attributes.values());
    }
}
