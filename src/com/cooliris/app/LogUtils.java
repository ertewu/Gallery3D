package com.cooliris.app;

import android.util.Log;

public class LogUtils {

    public static void log(Object str) {
        Log.i("ertewu", "" + str);
    }

    public static void footPrint(){
        String msgToPrint="";
        String className = Thread.currentThread().getStackTrace()[4].getClassName();
        int index = className.lastIndexOf(".");
        if (index > -1) {
            className = className.substring(index + 1);
        }

       String invokeclass = Thread.currentThread().getStackTrace()[5].getClassName();
       index = invokeclass.lastIndexOf(".");
        if (index > -1) {
            invokeclass = invokeclass.substring(index + 1);
        }

        className=className+"--"+invokeclass;

        String methodName= Thread.currentThread().getStackTrace()[4].getMethodName()+"--"+Thread.currentThread().getStackTrace()[5].getMethodName();
        int rowIndex = Thread.currentThread().getStackTrace()[4].getLineNumber();

        msgToPrint =msgToPrint+"Thread id:" + Thread.currentThread().getId() + "|Thread name:"
                + Thread.currentThread().getName() + "|ClassName:" + className + "|MethodName:" + methodName
                + "|RowIndex:" + rowIndex + "\n";

        msgToPrint = msgToPrint + "\n";
//        log(msgToPrint);
        Log.i("ertewu2", msgToPrint);
    }

    /**
     * 这个footPrint是从项目中拿出来的，应该是最好用的
     */
    public static void printMethodInfo(String appendMsg) {
        String msgToPrint = "--------------printMethodInfo start------------------\n";
        String className = Thread.currentThread().getStackTrace()[3].getClassName();
        int index = className.lastIndexOf(".");
        if (index > -1) {
            className = className.substring(index + 1);
        }

        String methodName= Thread.currentThread().getStackTrace()[3].getMethodName();
        int rowIndex = Thread.currentThread().getStackTrace()[3].getLineNumber();

        msgToPrint =msgToPrint+"Thread id:" + Thread.currentThread().getId() + "|Thread name:"
                + Thread.currentThread().getName() + "|ClassName:" + className + "|MethodName:" + methodName
                + "|RowIndex:" + rowIndex + "\n";

        msgToPrint = msgToPrint + "\n";
        msgToPrint = msgToPrint + appendMsg + "\n";
        msgToPrint = msgToPrint + "--------------printMethodInfo end----------------";
        Log.i("ertewu", msgToPrint);
    }

    public static void printStackTrace(String appendMsg) {
        StringBuilder builder = new StringBuilder();
        builder.append("--------------printStackTrace start----------------\n");
        StackTraceElement[] array = Thread.currentThread().getStackTrace();
        if (null != array) {
            for (int i = 0; i < array.length; i++) {
                if (i > 1) {
                    StackTraceElement item = array[i];
                    String methodName = item.getMethodName();
                    String className = item.getClassName();
                    int rowIndex = item.getLineNumber();
                    // 因为className中有很多包名，看上去很不方便，所以我们只想让其显示最后一个点后边的东西，也就是类的名字
                    int classIndex = className.lastIndexOf(".");
                    className = className.substring(classIndex + 1);
                    String logStr = "Thread id:" + Thread.currentThread().getId() + "|Thread name:"
                            + Thread.currentThread().getName() + "|ClassName:" + className + "|MethodName:" + methodName
                            + "|RowIndex:" + rowIndex + "\n";
                    builder.append(logStr);
                }
            }
            builder.append(appendMsg + "\n");
            builder.append("--------------printStackTrace end----------------");
            log(builder.toString());
        }
    }

}
