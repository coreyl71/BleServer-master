package cc.noharry.bleserver.ble;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;

import cc.noharry.bleserver.bean.MsgBean;
import cc.noharry.bleserver.utils.L;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * @author NoHarry
 * @date 2018/03/27
 */

public class BLEAdmin {
    private static BLEAdmin INSTANCE = null;
    private Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final Handler mHandler;
    private BTStateReceiver btStateReceiver = null;
    private final BluetoothManager mBluetoothManager;
    private UUID UUID_ADV_SERVER = UUID.fromString("0000ffe3-0000-1000-8000-00805f9b34fb");
    private UUID UUID_SERVER = UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb");
    private UUID UUID_CHARREAD = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb");
    private UUID UUID_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
    private UUID UUID_CHARWRITE = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private BluetoothGattCharacteristic mCharacteristicWrite;
    private AdvertiseSettings mSettings;
    private AdvertiseData mAdvertiseData;
    private AdvertiseData mScanResponseData;
    private AdvertiseCallback mCallback;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private boolean isFirstRead = true;
    private Activity activity;

    /**
     * 数据包发过来的时候
     * 总包个数、即将发送的 msgId
     */
    private int totalCount;
    private int startMsgId;

//    private List<MsgBean> msgList;

    private IMsgReceive msgReceiveListener = null;

    private BLEAdmin(Context context) {
        this.activity = (Activity) context;
        if (context instanceof IMsgReceive) {
            msgReceiveListener = (IMsgReceive) context;
        } else {
            throw new IllegalArgumentException("activity must implements IMsgReceive");
        }
//        msgList = new ArrayList<>();
        if (null == msgIdSet) {
            msgIdSet = new HashSet<>();
        }
        mContext = context.getApplicationContext();
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (null != mBluetoothManager) {
            mBluetoothAdapter = mBluetoothManager.getAdapter();
        } else {
            mBluetoothAdapter = null;
        }
        mHandler = new Handler(mContext.getMainLooper());
        btStateReceiver = new BTStateReceiver();
    }

    public static BLEAdmin getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (BLEAdmin.class) {
                INSTANCE = new BLEAdmin(context);
            }
        }
        return INSTANCE;
    }


    /**
     * @param isEnableLog whther enable the debug log
     * @return BLEAdmin
     */
    public BLEAdmin setLogEnable(boolean isEnableLog) {
        if (isEnableLog) {
            L.isDebug = true;
        } else {
            L.isDebug = false;
        }
        return this;
    }

    /**
     * Return true if Bluetooth is currently enabled and ready for use.
     *
     * @return true if the local adapter is turned on
     */
    public boolean isEnable() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    /**
     * Turn on the local Bluetooth adapter
     *
     * @return true to indicate adapter startup has begun, or false on immediate error
     */
    public boolean openBT() {
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            return mBluetoothAdapter.enable();
        }
        return false;
    }

    private OnBTOpenStateListener btOpenStateListener = null;

//    public interface OnBTOpenStateListener {
//        void onBTOpen();
//    }

    /**
     * Turn on the local Bluetooth adapter with a listener on {@link BluetoothAdapter#STATE_ON}
     *
     * @param listener listen to the state of bluetooth adapter
     * @return true to indicate adapter startup has begun, or false on immediate error
     */
    public boolean openBT(OnBTOpenStateListener listener) {

        // 初始化蓝牙状态监听
        btOpenStateListener = listener;

        // 注册蓝牙状态的广播监听
        registerBtStateReceiver(mContext);

        if (null != mBluetoothAdapter) {

            // 如果蓝牙可用，则开启成功回调
            if (mBluetoothAdapter.isEnabled()) {
                btOpenStateListener.onBTOpen();
                return true;
            }
            // 判断是否开启成功
            if (!mBluetoothAdapter.isEnabled()) {
                return mBluetoothAdapter.enable();
            }

        }

        return false;

    }


    /**
     * Turn off the local Bluetooth adapter
     */
    public void closeBT() {
        if (null != mBluetoothAdapter && mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
        }
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return mBluetoothAdapter;
    }


    private void registerBtStateReceiver(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(btStateReceiver, filter);
    }

    private void unRegisterBtStateReceiver(Context context) {
        try {
            context.unregisterReceiver(btStateReceiver);
        } catch (Exception e) {
        } catch (Throwable e) {
        }

    }


    private class BTStateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context arg0, Intent intent) {
            String action = intent.getAction();
            L.i("action=" + action);
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent
                        .getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                L.i("state=" + state);
                switch (state) {
                    case BluetoothAdapter.STATE_TURNING_ON:
                        L.i("ACTION_STATE_CHANGED:  STATE_TURNING_ON");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        L.i("ACTION_STATE_CHANGED:  STATE_ON");
                        if (null != btOpenStateListener) {
                            btOpenStateListener.onBTOpen();
                        }
                        unRegisterBtStateReceiver(mContext);
                        break;
                    default:
                }
            }
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void initGATTServer() {

        /**
         * 广播的配置实例
         * // 设置广播的模式，低功耗，平衡和低延迟三种模式；
         * settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
         * // 设置广播的最长时间
         * settingsBuilder.setTimeout(0);
         * // 设置是否可以连接
         * setConnectable(boolean connectable);
         * // 设置广播的信号强度
         * setTxPowerLevel(int txPowerLevel);
         */
        mSettings = new AdvertiseSettings.Builder()
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .build();

        /**
         *  广播内容的实例
         *  长度限制 31 个字节，包含 UUIDs，DeviceInfo，Arbitrary service，Manufacturer data
         *  如果超出限制，会报错 AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE
         *  此错误在 AdvertiseCallback 的 onStartFailure() 方法中被 catch
         *  // 是否广播蓝牙名称
         *  setIncludeDeviceName(true);
         *  // 是否广播信号强度
         *  setIncludeTxPowerLevel(boolean includeTxPowerLevel)
         *  // 添加厂商信息，貌似不怎么用到。
         *  addManufacturerData(int manufacturerId, byte[] manufacturerSpecificData)
         *  // 添加服务进广播，即对外广播本设备拥有的服务。
         *  addServiceUuid(ParcelUuid serviceUuid),addServiceData(ParcelUuid serviceDataUuid, byte[] serviceData)
         */
        mAdvertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(new ParcelUuid(UUID_ADV_SERVER))
                .build();

        mScanResponseData = new AdvertiseData.Builder()
//        .addServiceUuid(new ParcelUuid(UUID_ADV_SERVER))
                .setIncludeTxPowerLevel(true)
                .addServiceData(new ParcelUuid(UUID_SERVER), new byte[]{0, 2, 3/*,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40*/})
                .build();

        // 31个字节
        String name = "Corey_MI5S_S";
//    String name = "LIF_BLE";

        byte[] bytes = name.getBytes();
        L.i("name.length = " + bytes.length);
        mBluetoothAdapter.setName(name);

        /**
         * Custom callback after Advertising succeeds or fails to start. Broadcasts the error code
         * in an Intent to be picked up by AdvertiserFragment and stops this Service.
         * 广播成功或者失败的回调
         */
        mCallback = new AdvertiseCallback() {

            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                L.i("BLE advertisement added successfully");
                initServices(mContext);

            }

            @Override
            public void onStartFailure(int errorCode) {
                L.e("Failed to add BLE advertisement, reason: " + errorCode);
            }
        };

        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        mBluetoothLeAdvertiser.startAdvertising(mSettings, mAdvertiseData, mScanResponseData, mCallback);

    }

    public void changeData() {
        mScanResponseData = new AdvertiseData.Builder()
//        .addServiceUuid(new ParcelUuid(UUID_SERVER))
                .setIncludeTxPowerLevel(true)
                .addServiceData(new ParcelUuid(UUID_SERVER), new byte[]{1, 2, 3/*,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40*/})
                .build();
        mBluetoothLeAdvertiser.stopAdvertising(mCallback);
        mBluetoothLeAdvertiser.startAdvertising(mSettings, mAdvertiseData, mScanResponseData, mCallback);
    }

    private BluetoothGattServer bluetoothGattServer;
    private BluetoothGattCharacteristic characteristicRead;
    private BluetoothDevice currentDevice;

    private void initServices(Context context) {

        /**
         * 获得 BluetoothGattServer 实例
         */
        bluetoothGattServer = mBluetoothManager.openGattServer(context, bluetoothGattServerCallback);
        /**
         * 为设备添加相应的 service
         */
        BluetoothGattService service = new BluetoothGattService(UUID_SERVER, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        /**
         * 为设备添加相应的 characteristic 和 descriptor（非必须，但通常都会用到）
         */
        // add a read characteristic.
        characteristicRead = new BluetoothGattCharacteristic(UUID_CHARREAD,
                BluetoothGattCharacteristic.PROPERTY_READ + BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        // add a descriptor
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID_DESCRIPTOR, BluetoothGattCharacteristic.PERMISSION_WRITE);
        characteristicRead.addDescriptor(descriptor);
        service.addCharacteristic(characteristicRead);

        // add a write characteristic.
        mCharacteristicWrite = new BluetoothGattCharacteristic(UUID_CHARWRITE,
                BluetoothGattCharacteristic.PROPERTY_WRITE
                        + BluetoothGattCharacteristic.PROPERTY_READ
                        + BluetoothGattCharacteristic.PROPERTY_NOTIFY
                ,
                BluetoothGattCharacteristic.PERMISSION_WRITE
                        + BluetoothGattCharacteristic.PERMISSION_READ
        );
        // add a descriptor
        mCharacteristicWrite.addDescriptor(descriptor);
        service.addCharacteristic(mCharacteristicWrite);

        bluetoothGattServer.addService(service);
        L.e("1、initServices ok");

    }

    // 接收消息的 ID 池，用来去重
    private HashSet<Integer> msgIdSet;
    // 获取开始和结束的时间
    private long startTimeMillis, endTimeMillis;

    /**
     * 服务事件的回调
     */
    private BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {

        /**
         * 3、连接状态发生变化时
         * @param device
         * @param status
         * @param newState
         */
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            L.e(String.format("3、onConnectionStateChange：device name = %s, address = %s", device.getName(), device.getAddress()));
            L.e(String.format("3、onConnectionStateChange：status = %s, newState =%s ", status, newState));
            super.onConnectionStateChange(device, status, newState);
            currentDevice = device;
        }

        /**
         * 2、成功添加服务
         * @param status
         * @param service
         */
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            L.e(String.format("onServiceAdded：status = %s", status));
        }

        /**
         * 远程设备请求读取数据
         * @param device
         * @param requestId
         * @param offset
         * @param characteristic
         */
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            L.e(String.format("onCharacteristicReadRequest：device name = %s, address = %s", device.getName(), device.getAddress()));
            L.e(String.format("onCharacteristicReadRequest：requestId = %s, offset = %s", requestId, offset));
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            L.e(String.format("onCharacteristicReadRequest：characteristic.getValue() = %s", new String(characteristic.getValue())));
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

        }

        /**
         * 3. 远程设备请求写入数据，接收具体的字节
         * @param device
         * @param requestId
         * @param characteristic
         * @param preparedWrite
         * @param responseNeeded
         * @param offset
         * @param requestBytes
         */
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] requestBytes) {
            L.e(String.format("3.onCharacteristicWriteRequest：device name = %s, address = %s", device.getName(), device.getAddress()));
            L.i("3.收到数据 hex:" + byte2HexStr(requestBytes) + " str:" + new String(requestBytes) + " 长度:" + requestBytes.length);

            // 用来判断 msgId 的缓存 byte 数组
            byte[] tempByte = new byte[4];
            System.arraycopy(requestBytes, 1, tempByte, 0, 4);

            int msgId = byteArrayToInt(tempByte);
            // 2019/11/28 根据 msgId 的定义规则做相应处理
            L.i("msgId = " + msgId);
            if (msgIdSet.contains(msgId)) {
                return;
            }
            // 加入 ID 池，方便下次判断
            msgIdSet.add(msgId);

            // 判断是首包还是内容包
            if (msgId == 1) {

                // 获取开始时间
                startTimeMillis = System.currentTimeMillis();

                // 暂定为1代表发送首包，内含即将要发的数据包的个数
                // 首包内代表数据包的个数的 byte 数组
                byte[] totalCountByte = new byte[4];
                System.arraycopy(requestBytes, 5, totalCountByte, 0, 4);
                byte[] startMsgIdByte = new byte[4];
                System.arraycopy(requestBytes, 9, startMsgIdByte, 0, 4);
                // 计算总包个数
                totalCount = byteArrayToInt(totalCountByte);
                // 计算即将发包的起始 msgId
                startMsgId = byteArrayToInt(startMsgIdByte);
                L.i("start---totalCount = " + totalCount);
                L.i("start---startMsgId = " + startMsgId);

            } else {

                // 内容字节数组
                byte[] contentByte = new byte[14];
                System.arraycopy(requestBytes, 5, contentByte, 0, 14);

                MsgBean msgBean = new MsgBean();
                msgBean.setHexStr(byte2HexStr(requestBytes));
                msgBean.setMsgShowStr(new String(requestBytes));
                msgBean.setMsgId(String.valueOf(msgId));
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        msgReceiveListener.onReceiveMsg(msgBean, contentByte);
                    }
                });

                L.i("contentMsgId = " + msgId);
                L.i("startMsgId = " + startMsgId);
                L.i("totalCount = " + totalCount);
                if (msgId == (startMsgId + totalCount - 1)) {
                    // 暂时只传四条：1000,1001,1002,1003
                    msgIdSet.clear();
                    // 获取结束时间
                    endTimeMillis = System.currentTimeMillis();
                    L.i("startTimeMillis = " + startTimeMillis);
                    L.i("endTimeMillis = " + endTimeMillis);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            msgReceiveListener.onReceiveMsgComplete();
                        }
                    });

                }

            }

            // 发送给client的响应
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            // 4.处理响应内容，通知客户端改变 Characteristic
            // TODO: 2019/12/2 试着在这里改变返回值，将这里的 requestBytes 改为写自己的 byte[]
            onResponseToClient(requestBytes, device, requestId, characteristic);
        }

        /**
         * 远程设备请求写入描述器
         * 2、描述被写入时，在这里执行 bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS...  收，触发 onCharacteristicWriteRequest
         * @param device
         * @param requestId
         * @param descriptor
         * @param preparedWrite
         * @param responseNeeded
         * @param offset
         * @param value
         */
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            L.e(String.format("2.onDescriptorWriteRequest：device name = %s, address = %s, value = %s", device.getName(), device.getAddress(), byte2HexStr(value)));
            //            L.e(TAG, String.format("2.onDescriptorWriteRequest：requestId = %s, preparedWrite = %s, responseNeeded = %s, offset = %s, value = %s,", requestId, preparedWrite, responseNeeded, offset, OutputStringUtil.toHexString(value)));

            // now tell the connected device that this was all successfull

            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }

        /**
         * 5.特征被读取。当回复响应成功后，客户端会读取然后触发本方法
         * @param device
         * @param requestId
         * @param offset
         * @param descriptor
         */
        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            L.e(String.format("onDescriptorReadRequest：device name = %s, address = %s", device.getName(), device.getAddress()));
            L.e(String.format("onDescriptorReadRequest：requestId = %s", requestId));
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
        }

        /**
         * 通知发送
         * @param device
         * @param status
         */
        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            L.e(String.format("5.onNotificationSent：device name = %s, address = %s", device.getName(), device.getAddress()));
            L.e(String.format("5.onNotificationSent：status = %s", status));
        }

        /**
         * mtu 改变
         * @param device
         * @param mtu
         */
        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            L.e(String.format("onMtuChanged：mtu = %s", mtu));
        }

        /**
         * 执行挂起写入操作
         * @param device
         * @param requestId
         * @param execute
         */
        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            L.e(String.format("onExecuteWrite：requestId = %s", requestId));
        }
    };


    /**
     * byte 数组转换成 int 整型
     *
     * @param b
     * @return
     */
    public static int byteArrayToInt(byte[] b) {
        byte[] a = new byte[4];
        int i = a.length - 1, j = b.length - 1;
        for (; i >= 0; i--, j--) {//从b的尾部(即int值的低位)开始copy数据
            if (j >= 0)
                a[i] = b[j];
            else
                a[i] = 0;//如果b.length不足4,则将高位补0
        }
        int v0 = (a[0] & 0xff) << 24;//&0xff将byte值无差异转成int,避免Java自动类型提升后,会保留高位的符号位
        int v1 = (a[1] & 0xff) << 16;
        int v2 = (a[2] & 0xff) << 8;
        int v3 = (a[3] & 0xff);
        return v0 + v1 + v2 + v3;
    }

    /**
     * 4.处理响应内容
     *
     * @param reqeustBytes
     * @param device
     * @param requestId
     * @param characteristic
     */
    private void onResponseToClient(byte[] reqeustBytes, BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic) {
        L.e(String.format("4.onResponseToClient：device name = %s, address = %s", device.getName(), device.getAddress()));
        L.e(String.format("4.onResponseToClient：requestId = %s", requestId));
        String msg = new String(reqeustBytes);
        L.i("4.收到 hex:" + byte2HexStr(reqeustBytes) + " str:" + msg);
        currentDevice = device;
        sendMessage(characteristic, "收到:" + msg);
    }

    /**
     * 服务端给客户端发消息
     *
     * @param txBuffer
     * @return
     */
    public boolean sendMessage(byte[] txBuffer) {

        boolean result = false;

        L.i("mCharacteristicWrite = " + mCharacteristicWrite);
        L.i("currentDevice = " + currentDevice);

        mCharacteristicWrite.setValue(txBuffer);
        if (currentDevice != null && null != bluetoothGattServer) {
            result = bluetoothGattServer.notifyCharacteristicChanged(currentDevice, mCharacteristicWrite, false);
            L.i("Server 主动发送 hex:" + byte2HexStr(txBuffer) + " str:" + new String(txBuffer));
        } else {
            L.e("写失败");
        }

        return result;

    }

    private void sendMessage(BluetoothGattCharacteristic characteristic, String message) {
        characteristic.setValue(message.getBytes());
        if (currentDevice != null) {
            bluetoothGattServer.notifyCharacteristicChanged(currentDevice, characteristic, false);
        }
        L.i("4.notify发送 hex:" + byte2HexStr(message.getBytes()) + " str:" + message);
    }


    private void sendMessage(BluetoothGattCharacteristic characteristic, byte[] message) {
        characteristic.setValue(message);
        if (currentDevice != null) {
            bluetoothGattServer.notifyCharacteristicChanged(currentDevice, characteristic, false);
        }

        L.i("4.notify发送 hex:" + byte2HexStr(message) + " str:" + new String(message));
    }

    public String byte2HexStr(byte[] value) {
        char[] chars = "0123456789ABCDEF".toCharArray();
        StringBuilder sb = new StringBuilder("");
        int bit;

        for (int i = 0; i < value.length; i++) {
            bit = (value[i] & 0x0F0) >> 4;
            sb.append(chars[bit]);
            bit = value[i] & 0x0F;
            sb.append(chars[bit]);
            if (i != value.length - 1) {
                sb.append('-');
            }

        }
        return "(0x) " + sb.toString().trim();
    }
}
