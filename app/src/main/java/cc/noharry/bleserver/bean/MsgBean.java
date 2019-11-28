package cc.noharry.bleserver.bean;

public class MsgBean {

    private String msgId;
    private String hexStr;
    private String msgShowStr;

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getHexStr() {
        return hexStr;
    }

    public void setHexStr(String hexStr) {
        this.hexStr = hexStr;
    }

    public String getMsgShowStr() {
        return msgShowStr;
    }

    public void setMsgShowStr(String msgShowStr) {
        this.msgShowStr = msgShowStr;
    }

    @Override
    public String toString() {
        return "MsgBean{" +
                "hexStr='" + hexStr + '\'' +
                ", msgShowStr='" + msgShowStr + '\'' +
                '}';
    }
}
