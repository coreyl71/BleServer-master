package cc.noharry.bleserver.ble;

public interface IMsgReceive {
    void onReceiveMsg(int msg_type, byte[] contentByte);
}
