package com.fsoft.nfcrewrite;

import android.nfc.tech.MifareUltralight;
import android.util.Log;

import java.util.ArrayList;

/**
 * class read anything about card
 * Created by Dr. Failov on 01.11.2015.
 */

public class MifareUltralightProcessor{
    private String TAG = "MifareUltralightProcessor";
    private ArrayList<Byte[]> pages = new ArrayList<Byte[]>();
    private int pagesCount = 0;
    private String page2Binary = null;
    private int pageSize = 4;

    public MifareUltralightProcessor(MifareUltralight mifareUltralight) {
        try {
            Log.d(TAG, "Connecting to card...");
            if(!mifareUltralight.isConnected())
                mifareUltralight.connect();
            Log.d(TAG, "Reading contents...");
            Log.d(TAG, "------------------");
            try {
                for (int i = 0; ; i++) {
                    Log.d(TAG, "PAGE = " + i);
                    pagesCount = i;
                    byte[] pagedata = mifareUltralight.readPages(i);
                    byte[] page = new byte[4];
                    System.arraycopy(pagedata, 0, page, 0, 4);
                    Byte[] oPage = null;
                    pages.add(oPage = toObjects(page));
                    Log.d(TAG, "DATA = " + separateBytes(toBinary(oPage)));
                    Log.d(TAG, "------------------");
                }
            }
            catch (Exception e){
                Log.d(TAG, "! Reached end of the memory.");
                Log.d(TAG, "------------------");
            }
            Log.d(TAG, "PAGES COUNT = " + pagesCount);
            if(pagesCount == 0){
                Log.d(TAG, "! Card is incorrect.");
                return;
            }
            page2Binary = toBinary(pages.get(2));
            Log.d(TAG, "PAGE2 = " + separateBytes(page2Binary));


            Log.d(TAG, "Read conditions: ");
            for (int i = 0; i < pagesCount; i++)
                Log.d(TAG, "Page "+i+": " + (isWritable(i)?"WRITABLE":"LOCKED"));
            mifareUltralight.close();
        }
        catch (Exception e){
            Log.d(TAG, "Error: " + e.toString());
        }
    }
    public boolean isCorrectCard(){
        return pagesCount > 0;
    }
    public boolean isWritable(int pageNumber){
        //if error
        if(page2Binary == null)
            return false;
        //system area
        if(pageNumber <= 3)
            return false;
        if(pageNumber > 15)
            return false;
        //block 10-15 lock
        if(pageNumber <= 15 && pageNumber >= 10 && page2Binary.charAt(21) == '1')
            return false;
        //block 4-6 lock
        if(pageNumber <= 9 && pageNumber >= 4 && page2Binary.charAt(22) == '1')
            return false;

        //locked bits
        if(pageNumber == 4)
            return page2Binary.charAt(19) == '0';
        if(pageNumber == 5)
            return page2Binary.charAt(18) == '0';
        if(pageNumber == 6)
            return page2Binary.charAt(17) == '0';
        if(pageNumber == 7)
            return page2Binary.charAt(16) == '0';
        if(pageNumber == 15)
            return page2Binary.charAt(24) == '0';
        if(pageNumber == 14)
            return page2Binary.charAt(25) == '0';
        if(pageNumber == 13)
            return page2Binary.charAt(26) == '0';
        if(pageNumber == 12)
            return page2Binary.charAt(27) == '0';
        if(pageNumber == 11)
            return page2Binary.charAt(28) == '0';
        if(pageNumber == 10)
            return page2Binary.charAt(29) == '0';
        if(pageNumber == 9)
            return page2Binary.charAt(30) == '0';
        if(pageNumber == 8)
            return page2Binary.charAt(31) == '0';

        return true;
    }
    public int getPagesCount(){
        return pagesCount;
    }
    public int getPageSize(){
        return pageSize;
    }
    public Byte[] getPage(int page){
        return pages.get(page);
    }

    public static Byte[] toObjects(byte[] bytesPrim) {
        Byte[] bytes = new Byte[bytesPrim.length];
        int i = 0;
        for (byte b : bytesPrim) bytes[i++] = b; // Autoboxing
        return bytes;
    }
    public static byte[] toPrimitives(Byte[] oBytes){
        byte[] bytes = new byte[oBytes.length];

        for(int i = 0; i < oBytes.length; i++) {
            bytes[i] = oBytes[i];
        }

        return bytes;
    }
    public static String toBinary(Byte[] bytes){
        StringBuilder sb = new StringBuilder(bytes.length * Byte.SIZE);
        for( int i = 0; i < Byte.SIZE * bytes.length; i++ )
            sb.append((bytes[i / Byte.SIZE] << i % Byte.SIZE & 0x80) == 0 ? '0' : '1');
        return sb.toString();
    }
    public static String separateBytes(String binary){
        StringBuilder sb = new StringBuilder();
        for( int i = 0; i < binary.length(); i++ ) {
            if(i%8==0)
                sb.append(" ");
            sb.append(binary.charAt(i));
        }
        return sb.toString();
    }
    public static String toString(Byte[] bytes){
        try {
            return new String(toPrimitives(bytes), "UTF-8");
        }
        catch (Exception e){
            String s = "";
            for (Byte b:bytes) s+=(char)b.byteValue();
            return s;
        }
    }
}