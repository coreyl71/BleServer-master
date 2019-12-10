package cc.noharry.bleserver.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class MySP {

    public static void putStringShare(Context context, String whichSP, String key, String value) {

        SharedPreferences sharedPreferences =
                context.getSharedPreferences(whichSP, Context.MODE_PRIVATE);
        Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    public static String getStringShare(Context context, String whichSP, String key, String defValue){

        String s = "";
        //

        SharedPreferences sharedPreferences =
                context.getSharedPreferences(whichSP, Context.MODE_PRIVATE);
        s = sharedPreferences.getString(key,defValue);
        return s;
    }

    public static int getIntShare(Context context, String whichSP, String key, int defValue){

        int i ;

        SharedPreferences sharedPreferences =
                context.getSharedPreferences(whichSP, Context.MODE_PRIVATE);
        i = sharedPreferences.getInt(key, defValue);
        return i;
    }

    public static long getLongShare(Context context, String whichSP, String key, long defValue){

        long i ;

        SharedPreferences sharedPreferences =
                context.getSharedPreferences(whichSP, Context.MODE_PRIVATE);
        i = sharedPreferences.getLong(key, defValue);
        return i;
    }

    public static boolean getBooleanShare(Context context, String whichSP, String key, boolean defValue){

        Boolean b;

        SharedPreferences sharedPreferences =
                context.getSharedPreferences(whichSP, Context.MODE_PRIVATE);
        b = sharedPreferences.getBoolean(key, defValue);
        return b;
    }


    public static void putIntShare(Context context, String whichSP, String key, int value) {

        SharedPreferences sharedPreferences =
                context.getSharedPreferences(whichSP, Context.MODE_PRIVATE);
        Editor editor = sharedPreferences.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    public static void putLongShare(Context context, String whichSP, String key, long value) {

        SharedPreferences sharedPreferences =
                context.getSharedPreferences(whichSP, Context.MODE_PRIVATE);
        Editor editor = sharedPreferences.edit();
        editor.putLong(key, value);
        editor.apply();
    }

    public static void putBooleanShare(Context context, String whichSP, String key, boolean value) {

        SharedPreferences sharedPreferences =
                context.getSharedPreferences(whichSP, Context.MODE_PRIVATE);
        Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply(); //
    }

    public static void clearSP(Context context, String whichSP){

        //清除某个SP
        SharedPreferences sharedPreferences = context.getSharedPreferences(whichSP, Context.MODE_PRIVATE);
        Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();

    }

}
