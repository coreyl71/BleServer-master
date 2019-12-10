package cc.noharry.bleserver.ContentValue;

/**
 * auther:  create by farinaZhang
 * Date:  2018/11/13 0013
 * Description:
 * 记录一些字符常量
 **/
public class ContantValue {

    public static final String TAG = "farinaZhang";
    public static final int USER_KEYCODE_1 = 231;   // 25 ; //  231;
    public static final int USER_KEYCODE_2 = 27;   // 24 ; //   27;
    // 手机：25 音量+，确认；24 音量-，拒绝
//    public static final int USER_KEYCODE_1 = 25, USER_KEYCODE_2 = 24;

    public static final String strDeviceType = "device_type";
    public static final String strDeviceState = "device_state";
    public static final String strBackColor= "back_color";
    public static final String strLanguage = "language";
    public static final String strLocCheack = "beCheak";//定位追踪
    public static final String strMedicalCheack = "beMedial"; //用药提醒开启
    public static final String strGenceCheack = "beGeoFence"; //电子围栏开启

    public static final String productDate = "productDate"; //生产日期
    public static final String versionNumber = "versionNumber"; //软件版本号
    public static final String hardNumber = "hardNumber";//硬件版本号
    public static final String DeviceId = "DeviceId"; //设备序列号
    public static final String DevicePsw = "DevicePsw"; //设备密码
    public static final String DeviceQR = "DeviceQRCode"; //设备二维码
    public static final String SWTime = "softVersionTime"; //软件更新时间
    public static final int NONE_INT = -1;
    public static final String NONE_STRINT = null;


    /**正常数据存储频率; 15分钟   15*60*1000  **/
    public static final int SAVE_DATE_RATE = 15*60*1000;
    /**定位追踪/电子围栏 定位 频率2分钟**/
    public static final int LOCAL_RATE = 15*60*1000;
    /**报警状态发生时，数据存储频率并上传5分钟。**/
    public static final int SAVE_SOS_DATA_RATE = 15*60*1000;

    /**报警持续上传数据时间 30分钟**/
    public static final int SOS_TOTAL_TIME = 15*2*60*1000;  //2+1次

    /**定时开机后每12小时向服务器上传一次数据   12*60*60*1000;**/
    public static final int TIME_PUSH_DATA_RATE = 12*3600*1000 ;

    public static final int GET_SONSOR_RATE = 500; //传感器数据采集频率  20ms


    public static final String SP_BLE_REMOTE_INFO = "remoteInfo";
    public static final String SP_BLE_REMOTE_TOKEN = "remoteToken";


}
