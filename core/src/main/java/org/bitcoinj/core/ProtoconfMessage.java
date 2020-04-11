package org.bitcoinj.core;


public class ProtoconfMessage extends Message {

    private long numberOfFields;
    private long maxRecvPayloadLength;

    public ProtoconfMessage(NetworkParameters params, byte[] payloadBytes) throws ProtocolException {
        super(params, payloadBytes, 0);
    }

    @Override
    public String toString() {
        return "TempProtoconfMessage:  numberOfFields: " + numberOfFields + (numberOfFields < 1 ? "" :
                "  maxRecvPayloadLength: " + maxRecvPayloadLength);
    }

    @Override
    protected void parse() throws ProtocolException {

        numberOfFields = readVarInt();
        if (numberOfFields > 0) {
            maxRecvPayloadLength = readUint32();
        }

        length = cursor - offset;
    }


    public long getNumberOfFields() {
        return numberOfFields;
    }

    public void setNumberOfFields(long numFields) {
        this.numberOfFields = numFields;
    }

    public long getMaxRecvPayloadLength() {
        return maxRecvPayloadLength;
    }

    public void setMaxRecvPayloadLength(long len) {
        this.maxRecvPayloadLength = len;
    }
}
