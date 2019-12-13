package cc.noharry.bleserver.utils;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class AssetsUtil {

    /**
     * 读取 assets 本地 json
     * @param fileName
     * @param context
     * @return
     */
    public static String getJson(String fileName, Context context) {

        //将json数据变成字符串
        StringBuilder stringBuilder = new StringBuilder();

        try {

            //获取assets资源管理器
            AssetManager assetManager = context.getAssets();

            //通过管理器打开文件并读取
            BufferedReader bf = new BufferedReader(new InputStreamReader(
                    assetManager.open(fileName)));

            String line;
            while ((line = bf.readLine()) != null) {
                stringBuilder.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return stringBuilder.toString();

    }

    /**
     * 读取 assets 本地 json，返回 byte[]
     * @param fileName
     * @param context
     * @return
     */
    public static byte[] getJsonBytes(String fileName, Context context) {

        byte[] dataBytes = null;

        try {

            //获取assets资源管理器
            AssetManager assetManager = context.getAssets();
            //通过管理器打开文件并读取
            BufferedReader bf = new BufferedReader(new InputStreamReader(
                    assetManager.open(fileName)));

            // 拼装的 StringBuffer
            StringBuffer sb = new StringBuffer("");
            // 每行读取的字符串
            String str;

            // 遍历读取
            while ((str = bf.readLine()) != null) {
                sb.append(str);
            }

            String resultStr = sb.toString();

            // 字符串转换成 Byte 数组
            dataBytes = resultStr.getBytes(StandardCharsets.UTF_8);

            } catch (IOException e) {
                e.printStackTrace();
            }

        return dataBytes;

    }

}
