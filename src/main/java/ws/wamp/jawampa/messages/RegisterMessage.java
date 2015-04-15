package ws.wamp.jawampa.messages;

import ws.wamp.jawampa.ApplicationError;
import ws.wamp.jawampa.WampError;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A Callees request to register an endpoint at a Dealer. [REGISTER,
 * Request|id, Options|dict, Procedure|uri]
 */
public class RegisterMessage extends WampMessage {
    public final static int ID = 64;
    public final long requestId;
    public final ObjectNode options;
    public final String procedure;

    public RegisterMessage(long requestId, ObjectNode options, String procedure) {
        this.requestId = requestId;
        this.options = options;
        this.procedure = procedure;
    }

    public JsonNode toObjectArray(ObjectMapper mapper) throws WampError {
        ArrayNode messageNode = mapper.createArrayNode();
        messageNode.add(ID);
        messageNode.add(requestId);
        if (options != null)
            messageNode.add(options);
        else
            messageNode.add(mapper.createObjectNode());
        messageNode.add(procedure.toString());
        return messageNode;
    }

    static class Factory implements WampMessageFactory {
        @Override
        public WampMessage fromObjectArray(ArrayNode messageNode) throws WampError {
            if (messageNode.size() != 4
                    || !messageNode.get(1).canConvertToLong()
                    || !messageNode.get(2).isObject()
                    || !messageNode.get(3).isTextual())
                throw new WampError(ApplicationError.INVALID_MESSAGE);

            long requestId = messageNode.get(1).asLong();
            ObjectNode options = (ObjectNode) messageNode.get(2);
            String procedure = messageNode.get(3).asText();

            return new RegisterMessage(requestId, options, procedure);
        }
    }
}