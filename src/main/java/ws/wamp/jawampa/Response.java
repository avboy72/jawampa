/*
 * Copyright 2014 Matthias Einwag
 *
 * The jawampa authors license this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package ws.wamp.jawampa;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import ws.wamp.jawampa.internal.UriValidator;
import ws.wamp.jawampa.io.BaseClient;
import ws.wamp.jawampa.messages.ErrorMessage;
import ws.wamp.jawampa.messages.InvocationMessage;
import ws.wamp.jawampa.messages.YieldMessage;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Holds the arguments for a WAMP remote procedure call and provides methods
 * to send responses to the caller.<br>
 * Either {@link #reply(ArrayNode, ObjectNode)} or 
 * {@link #replyError(String, ArrayNode, ObjectNode)}} should be called in
 * order to send a positive or negative response back to the caller.
 */
public class Response {
    
    final BaseClient baseClient;
    final long requestId;
    final ArrayNode arguments;
    final ObjectNode keywordArguments;
    
    volatile int replySent = 0;
    
    private static final AtomicIntegerFieldUpdater<Response> replySentUpdater;
    static {
        replySentUpdater = AtomicIntegerFieldUpdater.newUpdater(Response.class, "replySent");
    }
    
    public ArrayNode arguments() {
        return arguments;
    }
    
    public ObjectNode keywordArguments() {
        return keywordArguments;
    }

    public Response(BaseClient baseClient, 
                   long requestId, ArrayNode arguments, ObjectNode keywordArguments)
    {
        this.baseClient = baseClient;
        this.requestId = requestId;
        this.arguments = arguments;
        this.keywordArguments = keywordArguments;
    }
    
    /**
     * Send an error message in response to the request.<br>
     * If this is called more than once then the following invocations will
     * have no effect. Respones will be only sent once.
     * @param error The ApplicationError that shoul be serialized and sent
     * as an exceptional response. Must not be null.
     */
    public void replyError(ApplicationError error) throws ApplicationError{
        if (error == null || error.uri == null) throw new NullPointerException();
        replyError(error.uri, error.args, error.kwArgs);
    }
    
    /**
     * Send an error message in response to the request.<br>
     * If this is called more than once then the following invocations will
     * have no effect. Respones will be only sent once.
     * @param errorUri The error message that should be sent. This must be a
     * valid WAMP Uri.
     * @param arguments The positional arguments to sent in the response
     * @param keywordArguments The keyword arguments to sent in the response
     */
    public void replyError(String errorUri, ArrayNode arguments, ObjectNode keywordArguments) throws ApplicationError {
        int replyWasSent = replySentUpdater.getAndSet(this, 1);
        if (replyWasSent == 1) return;
        
        UriValidator.validate(errorUri);
        
        final ErrorMessage msg = new ErrorMessage(InvocationMessage.ID, 
                                                  requestId, null, errorUri,
                                                  arguments, keywordArguments);

        baseClient.scheduleMessageToRouter( msg );
    }
    
    /**
     * Send a normal response to the request.<br>
     * If this is called more than once then the following invocations will
     * have no effect. Respones will be only sent once.
     * @param arguments The positional arguments to sent in the response
     * @param keywordArguments The keyword arguments to sent in the response
     */
    public void reply(ArrayNode arguments, ObjectNode keywordArguments) {
        int replyWasSent = replySentUpdater.getAndSet(this, 1);
        if (replyWasSent == 1) return;
        
        final YieldMessage msg = new YieldMessage(requestId, null,
                                                  arguments, keywordArguments);

        baseClient.scheduleMessageToRouter( msg );
    }
}