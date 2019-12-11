package cc.noharry.bleserver.ContentValue;

public class BFrameConst {

    public static byte FRAME_HEAD = (byte)0xFF;
    public static int  MESSAGE_ID_LENGTH = 4;
    public static byte FRAME_END = (byte)0x00;

    /**
     * 发送主设备唯一标识码的数据包起始 msgId
     */
    public static final int START_MSG_ID_UNIQUE = 1000;
    public static final int START_MSG_ID_CONTENT = 2000;
    /**
     * 开始发送消息的数据包起始 msgId
     */
    public static final int START_MSG_ID_START = 1;
    /**
     * Server 端提示需要主设备发送 token，用来给 Server 端做校验
     */
    public static final int START_MSG_ID_NEED_TOKEN = 2;

    /**
     * 之后将这里改为本机唯一标识码
     */
    public static final String TOKEN = "token1";

}