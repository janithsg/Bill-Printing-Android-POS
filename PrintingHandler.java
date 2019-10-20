package com.test.xyz.printing;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.test.xyz.R;
import com.test.xyz.printing.common.MessageType;
import com.smartdevice.aidl.IZKCService;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

public class PrintingHandler {
    Context context;
    private static PrintingHandler pHandler;

    // Flags
    public static int module_flag = 0; // 0 = Printer << Module to be loaded
    public static int DEVICE_MODEL = 0;

    // Device Service
    public static IZKCService mIzkcService;

    // Singleton Implementation ------ Start
    private PrintingHandler(Context context) {
        this.context = context;

        // Connect to Device Service
        bindService();
    }

    public static PrintingHandler getPrintingInstance(Context context) {
        if (pHandler == null) {
            pHandler = new PrintingHandler(context);
        }

        return pHandler;
    }
    // Singleton Implementation ------ End

    // Check whether the printer is there.
    public Boolean isPrinterExist() {
        if (mIzkcService != null) {
            return true;
        }

        return false;
    }

    // Service Interfacing
    private ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e("client", "onServiceDisconnected");
            mIzkcService = null;
            Toast.makeText(context, "Printer disconnected.", Toast.LENGTH_SHORT).show();

            // Notify Connection Fail
            sendEmptyMessage(MessageType.BaiscMessage.SEVICE_BIND_FAIL);

            // Clear the instance
            pHandler = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e("client", "onServiceConnected");
            mIzkcService = IZKCService.Stub.asInterface(service);
            if(mIzkcService!=null){
                try {
                    Toast.makeText(context, "Printer Connected.", Toast.LENGTH_SHORT).show();

                    // Get printer model
                    DEVICE_MODEL = mIzkcService.getDeviceModel();

                    // Load function module
                    mIzkcService.setModuleFlag(module_flag);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                // Send message to tell Bind Success
                sendEmptyMessage(MessageType.BaiscMessage.SEVICE_BIND_SUCCESS);
            }
        }
    };

    // Bind to Device Service
    private void bindService() {
        Intent intent = new Intent("com.zkc.aidl.all");
        intent.setPackage("com.smartdevice.aidl");
        context.bindService(intent, mServiceConn, Context.BIND_AUTO_CREATE);
    }

    // Send Empty Message
    private void sendEmptyMessage(int what) {
        System.out.println("DeviceServiceMessages: " + what);
    }

    // Do Print
    public void printInvoice(JSONObject json) {
        if (mIzkcService != null) {
            String formattedTxt = formatJSONToString(json);
            new PrintingTask().execute(formattedTxt);
        } else {
            // Service has not binded yet. Show error.
            Toast.makeText(context, "Printer is not Ready.", Toast.LENGTH_SHORT).show();
        }
    }

    // Format JSON for Printing in the Bill
    private String formatJSONToString(JSONObject json) {
        String detail1 = "";
        String detail2 = "0000000000";
        String detail3 = "0000000000";
        String detail4 = "00000";
        String detail5 = "00000000";
        String detail6 = "0000";

        try {
            detail1 = json.getString("d1");
            detail2 = json.getString("d2");
            detail3 = json.getString("d3");
            detail4 = json.getString("d4");
            detail5 = json.getString("d5");
            detail6 = json.getString("d6");
        } catch (JSONException e) {
            System.out.printf("JSON Parsing Error");
        }

        // Date Time
        SimpleDateFormat sdfDate = new SimpleDateFormat("yy/MM/dd HH:mm");//dd/MM/yyyy
        Date now = new Date();
        String strDate = sdfDate.format(now);

        String printStr = "";

        printStr += formatLine("Detail 1", "SUCCESS");
        printStr += formatLine("Detail 2", detail1);
        printStr += formatLine("Detail 3", detail2);
        printStr += formatLine("Detail 4", detail3);
        printStr += formatLine("Detail 5", detail4);
        printStr += formatLine("Detail 6", detail5);
        printStr += formatLine("Detail 7", detail6);
        printStr += formatLine("Date / Time", strDate);

        return printStr;
    }

    // Format Single Line for Printing
    private String formatLine(String key,String value) {
        return String.format("%-14s:%17s\n", key, value);
    }

    // Background Thread to Handle the Printing
    private class PrintingTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... params) {
            try {
                if (mIzkcService.checkPrinterAvailable()) {
                    String stat = mIzkcService.getPrinterStatus();

                    if (stat.equals("Normal")) {
                        System.out.println("Printer Status: " + stat);

                        Bitmap header = BitmapFactory.decodeResource(context.getResources(), R.drawable.invoice_header);
                        Bitmap footer = BitmapFactory.decodeResource(context.getResources(), R.drawable.invoice_footer);

                        // Printing Length is 32
                        mIzkcService.printBitmapAlgin(header, 384,250, 1);

                        mIzkcService.printGBKText(params[0]);

                        mIzkcService.printBitmapAlgin(footer, 384,130, 1);
                        mIzkcService.printUnicodeText("\n\n\n");
                        mIzkcService.stopRunningTask();
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            System.out.println("Result: " + result);
        }
    }
}
