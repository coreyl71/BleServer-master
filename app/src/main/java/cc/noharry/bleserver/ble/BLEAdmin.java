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
import android.bluetooth.BluetoothProfile;
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
import android.support.annotation.RequiresApi;

import cc.noharry.bleserver.ContentValue.BFrameConst;
import cc.noharry.bleserver.utils.ByteUtil;
import cc.noharry.bleserver.utils.L;

import java.nio.charset.StandardCharsets;
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
     * 用户是否已授权，只有授权了才可以进行数据传输
     */
    private boolean isUserAuth;

    /**
     * 数据包发过来的时候
     * 总包个数、即将发送的 msgId
     */
    private int msgType;
    private int totalCount;
//    private int startMsgId;

//    private List<MsgBean> msgList;

    private IMsgReceive msgReceiveListener = null;
    private IMsgSend msgSendListener = null;

    private BLEAdmin(Context context) {
        this.activity = (Activity) context;
        if (context instanceof IMsgReceive) {
            msgReceiveListener = (IMsgReceive) context;
        } else {
            throw new IllegalArgumentException("activity must implements IMsgReceive");
        }
        if (context instanceof IMsgSend) {
            msgSendListener = (IMsgSend) context;
        } else {
            throw new IllegalArgumentException("activity must implements IMsgSend");
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
     * 如果当前蓝牙可用，则返回 true
     * @return true if the local adapter is turned on
     */
    public boolean isEnable() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    /**
     * 开启蓝牙适配器
     * @return true to indicate adapter startup has begun, or false on immediate error
     */
    public boolean openBT() {
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            return mBluetoothAdapter.enable();
        }
        return false;
    }

    private IBTOpenStateChange btOpenStateListener = null;

    /**
     * 开启蓝牙适配器，并通过接口的方式进行监听
     * Turn on the local Bluetooth adapter with a listener on {@link BluetoothAdapter#STATE_ON}
     *
     * @param listener listen to the state of bluetooth adapter
     * @return true to indicate adapter startup has begun, or false on immediate error
     */
    public boolean openBT(IBTOpenStateChange listener) {

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
     * 关闭蓝牙适配器
     */
    public void closeBT() {
        if (null != mBluetoothAdapter && mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
        }
    }

    /**
     * 获取蓝牙适配器对象
     * @return
     */
    public BluetoothAdapter getBluetoothAdapter() {
        return mBluetoothAdapter;
    }

    /**
     * 注册广播，监听蓝牙开启状态
     * @param context
     */
    private void registerBtStateReceiver(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(btStateReceiver, filter);
    }

    /**
     * 注销监听蓝牙状态的广播
     * @param context
     */
    private void unRegisterBtStateReceiver(Context context) {
        try {
            context.unregisterReceiver(btStateReceiver);
        } catch (Exception e) {
        } catch (Throwable e) {
        }

    }

    /**
     * 用户已授权，可以进行数据传输
     */
    public void agreeConnection() {
        isUserAuth = true;
        L.e("agreeConnection---isUserAuth = " + isUserAuth);
    }

    /**
     * 用户拒绝连接，断开 BLE 设备的连接
     */
    public void closeConnection() {

        isUserAuth = false;

        // TODO: 2019/12/9  断开已建立的连接，或尝试取消当前正在进行的连接尝试。
        L.i("connected = " + mBluetoothManager.getConnectionState(currentDevice, BluetoothProfile.GATT));
        if (null != currentDevice) {
            bluetoothGattServer.cancelConnection(currentDevice);
        }

        // 不要在这里重新开始广播，逻辑放到连接状态监听中
//        startAdvertiser();

    }

    /**
     * 主动给主设备发送消息
     * @param data   发送的字符串
     */
    public void sendMessageActively(String data, int msgType) {

        // 字符串转换成 Byte 数组
        int dataLength = data.getBytes(StandardCharsets.UTF_8).length;
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);

        // 定义新的数据包存放数据（加包头包尾），再加类型，再加包的个数
        byte[] dataFinalBytes = new byte[dataLength + 2 + 4 + 4];
        // 包头包尾加两个标识位
        dataFinalBytes[0] = (byte) 0xFF;
        dataFinalBytes[dataFinalBytes.length - 1] = (byte) 0x00;
        // 先放数据包类型
        byte[] msgStartIdByte = ByteUtil.int2byte(msgType);
        System.arraycopy(msgStartIdByte, 0, dataFinalBytes, 1, BFrameConst.MESSAGE_ID_LENGTH);
        // 再放总包长度
        int msgPackageCount = ((dataLength % 20 == 0) ? (dataLength / 20) : (dataLength / 20 + 1));
        byte[] msgPackageCountByte = ByteUtil.int2byte(msgPackageCount);
        System.arraycopy(msgPackageCountByte, 0, dataFinalBytes, 5, BFrameConst.MESSAGE_ID_LENGTH);
        // 中间放传输内容
        System.arraycopy(dataBytes, 0, dataFinalBytes, 9, dataLength);

        // 分包操作，这里将msgType直接放在了原数组中，所以不需要单独传
//        subpackageByte(dataBytes, msgType);
        subpackageByte(dataFinalBytes);

    }

    // 是否客户端断开等原因停止传输
    private boolean isStop = false;
    // 是否准备就绪写入
    private boolean isWritingEntity;
    // 最后一包是否自动补零
    private final boolean lastPackComplete = false;
    // 每个包固定长度 20，包括头、尾、msgId
    private int packLength = 20;
    /**
     * 数据分包
     *
     * @param data 数据源
     */
    private void subpackageByte(byte[] data) {

        // 连接间隔时间修改
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
//        }

//        isWritingEntity = true;
        // 数据源数组的指针
        int index = 9;
        // 数据总长度
        int dataLength = data.length;
        // 待传数据有效长度，最后一个包是否需要补零
        int availableLength = dataLength;

        // 重试次数
        int retryCount = 0;
        // 是否消息开始第一个包，内含消息分包的个数
        boolean isMsgStart = true;

        while (index < dataLength) {

            // 是否由于客户端断开等原因停止传输
            if (isStop) {

                L.e("停止传输");
                if (availableLength > 0) {

                    // TODO: 2019/12/17 之后需要保存数据，做断点续传


                }

                break;

            }

            // 未就绪，可能没收到返回，或未成功写入
            if (!isWritingEntity) {

                // 小于五次则等待，多于五次（250ms）则重发
                if (retryCount < 5) {

                    L.e("等待分包");
                    try {
                        Thread.sleep(20L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    retryCount++;
                    continue;

                } else {

                    // 重置次数，方便下次阻塞的时候计数
                    retryCount = 0;

                }

            }
            L.e("开始分包");
            // 开始分包，状态置为未就绪状态
            isWritingEntity = false;

            // 每包数据内容大小为 20
            int onePackLength = packLength;
            // 最后一包不足长度不会自动补零
            if (!lastPackComplete) {
                onePackLength = (availableLength >= packLength) ? packLength : availableLength;
            }

            // 实例化一个数据分包，长度为 20
//            byte[] txBuffer = new byte[onePackLength];
            byte[] txBuffer = new byte[packLength];

            byte[] msgIdByte;
            if (isMsgStart) {

                /**
                 * 首包数组拷贝
                 * 原数组
                 * 元数据的起始位置
                 * 目标数组
                 * 目标数组的开始起始位置
                 * 要 copy 的数组的长度
                 */
                // 首位 0x00，2-4 msgType，5-8 packageCount
                System.arraycopy(data, 0, txBuffer, 0, 9);

                // 单个数据包发送
                boolean result = sendMessage(txBuffer);

//                if (!result) {
//                    isWritingEntity = false;
//                }

                // 将是否为首包置为 false，后面的开始发正式数据
                isMsgStart = false;

            } else {

                for (int i = 0; i < onePackLength; i++) {
                    if (index < dataLength) {
                        txBuffer[i] = data[index++];
                    }
                }

                // 更新剩余数据长度
                availableLength -= onePackLength;

                // 单个数据包发送
                boolean result = sendMessage(txBuffer);

            }

            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


        }

        // 连接间隔时间修改
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
//        }
        L.e("写入完成");

    }


    /**
     * BLE 开启状态监听
     */
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
        String name = "Corey_MI5S_S1";
//    String name = "LIF_BLE";

        byte[] bytes = name.getBytes();
        L.i("name.length = " + bytes.length);
        mBluetoothAdapter.setName(name);

        /**
         * 获取广播对象
         */
        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();

        /**
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

        // 开始广播
        startAdvertiser();

    }

    /**
     * 开始广播
     */
    private void startAdvertiser() {

        L.e("startAdvertiser");

        // 开启广播
        mBluetoothLeAdvertiser.startAdvertising(mSettings, mAdvertiseData, mScanResponseData, mCallback);

    }

    /**
     * 停止广播
     */
    private void stopAdvertiser() {
        mBluetoothLeAdvertiser.stopAdvertising(mCallback);
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

    /**
     * 初始化 BluetoothGattServer 对象
     *
     * @param context 当前上下文
     */
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
         * @param newState 0 未连接；1 正在连接；2 已连接；3 正在取消连接
         */
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            L.e(String.format("3、onConnectionStateChange：device name = %s, address = %s", device.getName(), device.getAddress()));
            L.e(String.format("3、onConnectionStateChange：status = %s, newState =%s ", status, newState));
            super.onConnectionStateChange(device, status, newState);
            currentDevice = device;
            isUserAuth = false;

            // 不能在这里停止广播，否则会造成主设备无法回调 onServicesDiscovered
//            stopAdvertiser();

            // 判断连接状态，0 为未连接，2 为已连接
            if (newState == 2) {
                L.i("已连接");
                // 客户端连接，初始化控制变量，可以传输数据
                isStop = false;
            } else if (newState == 0) {
                // 客户端断开连接，需要停止传输数据
                isStop = true;
                // 断开连接，重新开始广播
                stopAdvertiser();
                startAdvertiser();
            }

        }

        /**
         * 2、成功添加服务
         * @param status
         * @param service
         */
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            L.e(String.format("2、onServiceAdded：status = %s", status));
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

            if (requestBytes[0] == (byte) 0xFF) {

                L.e("首包数据");

                // 开始传输数据，此时为首包
                // 用来判断 msgId 的缓存 byte 数组
                byte[] msgTypeBytes = new byte[4];
                System.arraycopy(requestBytes, 1, msgTypeBytes, 0, 4);

                // 获取数据包类型
                msgType = byteArrayToInt(msgTypeBytes);
                L.i("start---msgType = " + msgType);

                // 记录开始时间
                startTimeMillis = System.currentTimeMillis();

                // 首包内代表数据包的个数的 byte 数组
                byte[] totalCountByte = new byte[4];
                System.arraycopy(requestBytes, 5, totalCountByte, 0, 4);
                // 计算总包个数
                totalCount = byteArrayToInt(totalCountByte);
                L.i("start---totalCount = " + totalCount);

                // 首包接收完成，先跳出去，等待接收下一个包
                // 发送给client的响应
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());

                // 发送给client的响应
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
                // 4.处理响应内容，通知客户端改变 Characteristic，这里写特定的返回值，用于主设备判断是回调还是主动写入
                byte[] responseBytes = new byte[2];
                responseBytes[0] = BFrameConst.FRAME_HEAD;
                responseBytes[1] = BFrameConst.FRAME_HEAD;
                characteristic.setValue(responseBytes);
                if (null != currentDevice) {
                    bluetoothGattServer.notifyCharacteristicChanged(currentDevice, characteristic, false);
                }

            } else {

                // 根据数据类型，判断接收到的是哪种数据，分别做解析
                if (msgType == BFrameConst.START_MSG_ID_TOKEN) {

                    // 解析 TOKEN 校验数据包
                    parseTokenPackage(requestBytes, msgType);

                } else if (msgType == BFrameConst.START_MSG_ID_CENTRAL) {
                    L.e("解析普通内容包");
                    // 解析普通内容数据包
                    parseContentPackage(requestBytes, msgType);
                }

                // 发送给client的响应
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
                // 4.处理响应内容，通知客户端改变 Characteristic，这里写特定的返回值，用于主设备判断是回调还是主动写入
                byte[] responseBytes = new byte[2];
                responseBytes[0] = BFrameConst.FRAME_HEAD;
                responseBytes[1] = BFrameConst.FRAME_HEAD;
                characteristic.setValue(responseBytes);
                if (null != currentDevice) {
                    bluetoothGattServer.notifyCharacteristicChanged(currentDevice, characteristic, false);
                }
                // TODO: 2019/12/2 试着在这里改变返回值，将这里的 requestBytes 改为写自己的 byte[]
//            onResponseToClient(requestBytes, device, requestId, characteristic);

            }



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
     * 解析 Token 数据包的方法
     *
     * @param requestBytes
     * @param msg_type
     */
    private void parseTokenPackage(byte[] requestBytes, int msg_type) {

        // 内容字节数组
//        byte[] contentByte = new byte[14];
//        System.arraycopy(requestBytes, 5, contentByte, 0, 14);
        byte[] contentByte = new byte[20];
        System.arraycopy(requestBytes, 0, contentByte, 0, 20);

//        MsgBean msgBean = new MsgBean();
//        msgBean.setHexStr(byte2HexStr(requestBytes));
//        msgBean.setMsgShowStr(new String(requestBytes));
//        msgBean.setMsgId(msg_type);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                msgReceiveListener.onReceiveMsg(msg_type, contentByte);
            }
        });

        L.i("msgType = " + msg_type);
//        if (msgId == (startMsgId + totalCount - 1)) {
//            // 暂时只传四条：1000,1001,1002,1003
//            msgIdSet.clear();
//            // 获取结束时间
//            endTimeMillis = System.currentTimeMillis();
//            L.i("complete---startTimeMillis = " + startTimeMillis);
//            L.i("complete---endTimeMillis = " + endTimeMillis);
//            L.i("complete---耗时 = " + (endTimeMillis - startTimeMillis) + "ms---" + (endTimeMillis - startTimeMillis) / 1000 + "s");
//
//            // TODO: 2019/12/10  代表主设备发送 TOKEN 过来，等待校验
//            activity.runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    msgReceiveListener.onReceiveTokenComplete();
//                }
//            });
//
//        }

    }

    /**
     * 解析普通内容数据包的方法
     *
     * @param requestBytes
     * @param msg_type
     */
    private void parseContentPackage(byte[] requestBytes, int msg_type) {

        // 如果用户未授权，则不用进行解析了，并且关闭连接
        L.e("isUserAuth = " + isUserAuth);
        if (!isUserAuth) {
            closeConnection();
            return;
        }

        // 内容字节数组
//        byte[] contentByte = new byte[14];
//        System.arraycopy(requestBytes, 5, contentByte, 0, 14);
        byte[] contentByte = new byte[20];
        System.arraycopy(requestBytes, 0, contentByte, 0, 20);

//        MsgBean msgBean = new MsgBean();
//        msgBean.setHexStr(byte2HexStr(requestBytes));
//        msgBean.setMsgShowStr(new String(requestBytes));
//        msgBean.setMsgId(msg_type);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                msgReceiveListener.onReceiveMsg(msg_type, contentByte);
            }
        });

        L.i("contentMsgId = " + msg_type);

        // TODO: 2019/12/11 接收数据完成的判断放在接收数据的回调中，判断末尾一位是不是 0x00
//        if (msgId == (startMsgId + totalCount - 1)) {
//            // 暂时只传四条：1000,1001,1002,1003
//            msgIdSet.clear();
//            // 获取结束时间
//            endTimeMillis = System.currentTimeMillis();
//            L.i("complete---startTimeMillis = " + startTimeMillis);
//            L.i("complete---endTimeMillis = " + endTimeMillis);
//            L.i("complete---耗时 = " + (endTimeMillis - startTimeMillis) + "ms---" + (endTimeMillis - startTimeMillis) / 1000 + "s");
//
//            activity.runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    msgReceiveListener.onReceiveMsgComplete();
//                }
//            });
//
//        }

    }


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
//        L.i("4.收到 hex:" + byte2HexStr(reqeustBytes) + " str:" + msg);
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

        mCharacteristicWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

        mCharacteristicWrite.setValue(txBuffer);
        if (currentDevice != null && null != bluetoothGattServer) {
            result = bluetoothGattServer.notifyCharacteristicChanged(currentDevice, mCharacteristicWrite, false);
            L.i("Server 主动发送 hex:" + byte2HexStr(txBuffer) + " str:" + new String(txBuffer));
        } else {
            L.e("写失败");
        }

        return result;

    }

    /**
     * 服务端给客户端写回调
     *
     * @param characteristic
     * @param message
     */
    private void sendMessage(BluetoothGattCharacteristic characteristic, String message) {
        characteristic.setValue(message.getBytes());
        if (currentDevice != null) {
            bluetoothGattServer.notifyCharacteristicChanged(currentDevice, characteristic, false);
        }
//        L.i("4.notify发送 hex:" + byte2HexStr(message.getBytes()) + " str:" + message);
    }

    private String byte2HexStr(byte[] value) {
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

}
