package com.wzy.debug;

import android.util.Log;

public class LogUtils {

    public static void log(Object str) {
        Log.i("ertewu", "" + str);
    }

    /**
     * 能打印出当前函数的invoke，继而看出问题来.. 这个是我自己写的..
     *
     * @param tag
     */
    public static void footPrint(String tag) {
        String msgToPrint = Thread.currentThread().getId() + "|" + "."
                + Thread.currentThread().getStackTrace()[4].getMethodName() + "|"
                + Thread.currentThread().getStackTrace()[5].getMethodName() + "|"
                + Thread.currentThread().getStackTrace()[6].getMethodName();
        Log.i("ertewu", tag + ":" + msgToPrint);
    }

    /**
     * 这个footPrint是从项目中拿出来的，但是并不好用啊..
     */
    public static void footPrint2(String tag) {
        String className = Thread.currentThread().getStackTrace()[3].getClassName();
        int index = className.lastIndexOf(".");
        if (index > -1) {
            className = className.substring(index + 1);
        }
        String msgToPrint = Thread.currentThread().getId() + " " + className + "."
                + Thread.currentThread().getStackTrace()[3].getMethodName();
        Log.i(tag, msgToPrint);
    }

    /**
     * 这个是意思我不懂为什么是Thread.currentThread.getStackTrace()[3],那0,1,2是做什么的？所以我试一下
     * 我试出来了：下边标注一个例子,0,1,2都是Thread.currentThread.getStackTrace自身的栈；<br>
     * 3其实是调用这个footprint的函数，但是其实我也不想知道这个，我其实是想知道谁调用了footPrint3的父函数... <br>
     * 当然我目的也达到了,我就用这个函数了
     */
    /**
     * I/ertewu3(10646): 0|625 GLThread 625 VMStack getThreadStackTrace 0 <br>
     * I/ertewu3(10646): 1|625 GLThread 625 Thread getStackTrace 1 <br>
     * I/ertewu3(10646): 2|625 GLThread 625 LogUtils stackTraceDemo 2 <br>
     * I/ertewu3(10646): 3|625 GLThread 625 GridLayer computeVisibleItems 3 <br>
     * I/ertewu3(10646): 4|625 GLThread 625 GridLayer renderOpaque 4 <br>
     * I/ertewu3(10646): 5|625 GLThread 625 RenderView onDrawFrame 5 <br>
     * I/ertewu3(10646): 6|625 GLThread 625 GLSurfaceView$GLThread guardedRun 6 <br>
     * I/ertewu3(10646): 7|625 GLThread 625 GLSurfaceView$GLThread run 7 <br>
     * I/ertewu3(10646): --------------------------
     */
    public static void footPrint3(String tag) {
        StackTraceElement[] array = Thread.currentThread().getStackTrace();
        if (null != array) {
            for (int i = 0; i < array.length; i++) {
                if(i>=3){
                    StackTraceElement item = array[i];
                    String methodName = item.getMethodName();
                    String className = item.getClassName();
                    int lineNum=item.getLineNumber();
                    // 因为className中有很多包名，看上去很不方便，所以我们只想让其显示最后一个点后边的东西，也就是类的名字
                    int classIndex = className.lastIndexOf(".");
                    className = className.substring(classIndex + 1);
                    String logStr = Thread.currentThread().getId() + "_" + Thread.currentThread().getName() + ":" + className + "."
                            + methodName+","+"r"+lineNum;
                    Log.i(tag, i + "|" + logStr);
                }
            }
            Log.i(tag, "--------------------------");
        }
    }

}
