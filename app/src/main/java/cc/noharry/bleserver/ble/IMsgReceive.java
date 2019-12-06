package cc.noharry.bleserver.ble;

import android.bluetooth.BluetoothDevice;

import java.util.List;

import cc.noharry.bleserver.bean.MsgBean;

public interface IMsgReceive {
    void onApplyConnection(BluetoothDevice device);
    void onReceiveMsg(MsgBean msgBean, byte[] contentByte);
    void onReceiveMsgComplete();
    void onMsgResend();
}
