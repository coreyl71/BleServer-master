package cc.noharry.bleserver.utils;

import android.util.Log;

/**
 * auther:  create by farinaZhang
 * Date:  2018/11/20 0020
 * Description:  log 打印
 **/
public class LogUtil {


    public class Debug {
        /**
         * Indicated whether logging functionality is enabled.
         * 正式版本 关闭log, 修改 服务器 地址 ，修改 地区，修改 编译时间，修改版本号
         */
        public final static boolean ON = false;

        /**
         * Protectes Dubug class of instantiation
         */
        private Debug() {
        } // constructor
    }

    private static final String TAG = "farinaZhang  ";
    private static final String FILE_NAME = "data/log.txt";
    private static final int MaxBufferSize = 8 * 1024;

    // Debug Info
    public static void d(String sMessage) {
        if (Debug.ON) {
            d(TAG, sMessage);
        }
    }

    public static void d(String sTag, String sMessage) {
        if (Debug.ON) {
            if (null != sMessage) {
                Log.d(sTag, sMessage);
            }
        }
    }

    // Warning Info
    public static void w(String sTag, String sMessage) {
        if (Debug.ON) {
            if (null != sMessage) {
                Log.w(sTag, sMessage);
            }
        }
    }

    // Error Info
    public static void e(String sMessage) {
        if (Debug.ON) {
            if (null != sMessage) {
                e(TAG, sMessage);
            }
        }
    }

    public static void e(String sTag, String sMessage) {
        if (Debug.ON) {
            if (null != sMessage) {
                Log.e(sTag, sMessage);
            }
        }
    }

    public static void i(String tag, String content) {
        if (Debug.ON) {
            if (null != content) {
                int p = 2000;
                long length = content.length();
                if (length < p || length == p)
                    Log.d(tag, content);
                else {
                    while (content.length() > p) {
                        String logContent = content.substring(0, p);
                        content = content.replace(logContent, "");
                        Log.d(tag, logContent);
                    }
                    Log.d(tag, content);
                }
            }
        }
    }

    /**
     * 可以将网络获取的内容写入文件
     * @param traceInfo
     */
    public static void toFile(byte[] traceInfo) {
		/*if (Debug.ON && PhoneUtil.sdcard()) {
			File file = new File(FILE_NAME);
			try {
				file.createNewFile();
				BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(
						new FileOutputStream(file, true), MaxBufferSize);
				bufferedOutputStream.write(traceInfo);
				traceInfo = null;
				bufferedOutputStream.close();
			} catch (IOException e) {
				LogOutput.d(e.getMessage());
			}
		}*/
    }

}
