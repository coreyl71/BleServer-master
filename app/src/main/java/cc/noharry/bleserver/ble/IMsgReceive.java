package cc.noharry.bleserver.ble;

import java.util.List;

import cc.noharry.bleserver.bean.MsgBean;

public interface IMsgReceive {
    void onReceiveMsg(MsgBean msgBean, byte[] contentByte);
    void onReceiveMsgComplete();
    void onMsgResend();
}
