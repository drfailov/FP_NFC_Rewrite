package com.fsoft.nfcrewrite;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends Activity {
    private String TAG = "NFC Rewrite";
    private Handler handler = new Handler();
    TextView textView;
    LinearLayout layout;
    ArrayList<WriteOperation> pendingOperations = new ArrayList<>();
    private boolean NFCWaiting = false;


    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        //textView = (TextView)findViewById(R.id.textView);
        layout = (LinearLayout)findViewById(R.id.layout);
//        Intent intent = getIntent();
//        onNewIntent(intent);
        showWaitingText();
    }
    @Override protected void onResume() {
        super.onResume();
        Log.d("NFCRewrite", "RESUME");
        startWaiting();
    }
    @Override protected void onPause() {
        Log.d("NFCRewrite", "PAUSE");
        stopWaiting();
        super.onPause();
    }
    @Override public void onNewIntent(Intent intent) {
        if(intent == null)
            return;
        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if(tagFromIntent == null)
            return;
        Log.d(TAG, "TAG = " + tagFromIntent.toString());
        final MifareUltralight mifareUltralight = MifareUltralight.get(tagFromIntent);
        if(pendingOperations == null || pendingOperations.size() == 0) {
            showReadingText();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    processTag(mifareUltralight);
                }
            }).start();
        }
        else {
            writePending(mifareUltralight);
        }
    }

    private void startWaiting(){
        NfcManager manager = (NfcManager) getSystemService(Context.NFC_SERVICE);
        NfcAdapter adapter = manager.getDefaultAdapter();
        if(adapter == null){
            showText("Ваше устройство не поддерживает NFC\n" +
                    "Вы не можете использовать программу", R.drawable.disabled);
            return;
        }
        if(!adapter.isEnabled()){
            showText("Фукнция NFC выключена", R.drawable.disabled);
            addNfcSettingsButton("Включить NFC");
            addResetButton("Проверить снова");
            return;
        }

        if(!NFCWaiting) {
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
            IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
            try {
                ndef.addDataType("*/*");
            } catch (IntentFilter.MalformedMimeTypeException e) {
                throw new RuntimeException("fail", e);
            }
            IntentFilter[] intentFiltersArray = new IntentFilter[]{ndef};
            String[][] techListsArray = new String[][]{new String[]{MifareUltralight.class.getName()}};
            NfcAdapter.getDefaultAdapter(this).enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray);
            //showWaitingText();
            NFCWaiting = true;
        }
    }
    private void startWaitingDelayed(int ms){
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    showWaitingText();
                    startWaiting();
                }
                catch (Exception e){
                    //it's because user hided app
                }
            }
        }, ms);
    }
    private void stopWaiting(){
        if(NFCWaiting) {
            NfcAdapter.getDefaultAdapter(this).disableForegroundDispatch(this);
            NFCWaiting = false;
        }
    }


    private void processTag(MifareUltralight mifareUltralight){
        MifareUltralightProcessor mifareUltralightProcessor = new MifareUltralightProcessor(mifareUltralight);
        if(!mifareUltralightProcessor.isCorrectCard()){
            showErrorReadingText();
            startWaitingDelayed(2000);
            return;
        }

        textView = null;
        handler.post(new Runnable() {
            @Override
            public void run() {
                layout.removeAllViews();
                TextView header = new TextView(MainActivity.this);
                header.setTextColor(Color.WHITE);
                header.setTextSize(20);
                header.setText("Содержимое карты:");
                layout.addView(header);

                FrameLayout frameLayout = new FrameLayout(MainActivity.this);
                frameLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100));
                layout.addView(frameLayout);
            }
        });

        boolean lastWritable = false;
        ArrayList<Byte> lastText = new ArrayList<>();
        int blockBeginPage = 0;
        int blockEndPage = 0;


        for (int i = 0; i < mifareUltralightProcessor.getPagesCount(); i++) {
            Byte[] data = (mifareUltralightProcessor.getPage(i));
            boolean writable = mifareUltralightProcessor.isWritable(i);
            if(lastWritable == writable){
                for (int j = 0; j < data.length; j++)
                    lastText.add(data[j]);
                blockEndPage = i;
            }
            else {
                addBlock(lastWritable, getString(lastText), blockBeginPage,blockEndPage);

                blockBeginPage = i;
                blockEndPage = i;
                lastText.clear();
                for (int j = 0; j < data.length; j++)
                    lastText.add(data[j]);
            }
            lastWritable = writable;
        }
        addBlock(lastWritable, getString(lastText), blockBeginPage,blockEndPage);
        addResetButton("Прочесть другую карту");
    }
    private void showWaitingText(){
        pendingOperations.clear();
        showText("NFC активен.\nПриложите карту...", R.drawable.nfc);
        addNfcSettingsButton("Выключить NFC");
    }
    private void showReadingText(){
        pendingOperations.clear();
        showText("Чтение карты...", R.drawable.nfc);
    }
    private void showErrorReadingText(){
        pendingOperations.clear();
        showText("Ошибка чтения карты", R.drawable.disabled);
    }
    private void addNfcSettingsButton(final String text){
        handler.post(new Runnable() {
            @Override
            public void run() {
                Button button = new Button(getBaseContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.CENTER;

                Resources r = getResources();
                lp.topMargin = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, r.getDisplayMetrics());
                button.setLayoutParams(lp);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
                            startActivity(intent);
                        } else {
                            Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                            startActivity(intent);
                        }
                    }
                });
                button.setText(text);
                layout.addView(button);
            }
        });
    }
    private void showText(final String text, final int imageResource){
        Log.d("NFCRewrite", "Show: " + text);
        handler.post(new Runnable() {
            @Override
            public void run() {
                layout.removeAllViews();

                if(imageResource != 0){
                    ImageView imageView = new ImageView(getBaseContext());
                    Resources r = getResources();
                    int px = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60, r.getDisplayMetrics());

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px, px);
                    lp.gravity = Gravity.CENTER;
                    lp.bottomMargin = px/2;
                    imageView.setLayoutParams(lp);
                    imageView.setImageResource(imageResource);
                    layout.addView(imageView);
                }

                textView = new TextView(getBaseContext());
                textView.setText(text);
                textView.setTextSize(20);
                textView.setGravity(Gravity.CENTER);
                layout.addView(textView);
            }
        });
    }
    private void addBlock(final boolean writable, final String text, final int begin, final int end){
        Log.d(TAG, "Add block: w="+writable+" t="+text+" b="+begin+" e="+end);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if(writable){
                    addWritableBlock(text, begin, end);
                }
                else {
                    TextView header = new TextView(MainActivity.this);
                    header.setText("Блок только для чтения: ");
                    layout.addView(header);

                    TextView textView1 = new TextView(MainActivity.this);
                    textView1.setText(text);
                    textView1.setTextSize(17);
                    textView1.setTextColor(Color.WHITE);
                    layout.addView(textView1);
                }

                FrameLayout frameLayout = new FrameLayout(MainActivity.this);
                frameLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100));
                layout.addView(frameLayout);
            }
        });
    }
    private void addResetButton(final String text){
        handler.post(new Runnable() {
            @Override
            public void run() {
                Button resetButton = new Button(MainActivity.this);
                resetButton.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.CENTER;

                Resources r = getResources();
                lp.topMargin = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, r.getDisplayMetrics());
                resetButton.setLayoutParams(lp);

                resetButton.setText(text);
                resetButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showWaitingText();
                        startWaiting();
                        //showText();
                        //pendingOperations.clear();
                    }
                });
                layout.addView(resetButton);
            }
        });
    }
    private void addWritableBlock(final String text, final int begin, final int end){
        TextView header = new TextView(this);
        header.setText("Перезаписываемый блок: ");
        layout.addView(header);


        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setGravity(Gravity.CENTER);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);

        final int maxLength = (end - begin + 1) * 4;

        final TextView textViewAvailable = new TextView(this);
        textViewAvailable.setText("Доступно байт для записи: " + maxLength);


        final EditText editText = new EditText(this);
        editText.setText(text.replace("`", ""));
        editText.setSingleLine(false);
        editText.setEnabled(true);
        editText.setTextSize(27);
        editText.setBackgroundColor(Color.parseColor("#444444"));
        editText.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextWatcher editTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    String text = editText.getText().toString();
                    int actual = text.getBytes("UTF-8").length;
                    textViewAvailable.setText("Осталось байт: " + (maxLength - actual));
                    //Log.d(TAG, "checking: " + text);
                    while ((actual = text.getBytes("UTF-8").length) > maxLength){
                        Log.d(TAG, "cutting "+actual+" bytes to "+maxLength+" bytes : " + text);
                        text = text.substring(0, text.length()-1);
                        if(actual <= maxLength) {
                            editText.setText(text);
                            editText.setSelection(editText.getText().length());
                        }
                    }
                }
                catch (Exception e){
                    //fuck
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };

        editText.addTextChangedListener(editTextWatcher);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        linearLayout.addView(editText);

        Button saveButton = new Button(this);
        saveButton.setTextSize(12);
        saveButton.setText("Сохр.");
        saveButton.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0));
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!editText.isEnabled())
                    return;
                editText.setEnabled(false);
                editText.setBackgroundColor(Color.BLACK);
                String text = editText.getText().toString();
                editText.removeTextChangedListener(editTextWatcher);
                //editText.setText("Для записи данных на карту приложите карту снова.");
                textViewAvailable.setText("Для записи данных на карту приложите карту снова.");
                editText.setTextSize(16);
                Log.d(TAG, "text = " + text);
                byte[] data = new byte[maxLength];
                for (int i = 0; i < data.length; i++) {
                    data[i] = (byte) '`';
                }
                byte[] byteText;
                try {
                    byteText = text.getBytes("UTF-8");
                } catch (Exception e) {
                    byteText = new byte[maxLength];
                }
                for (int i = 0; i < Math.min(byteText.length, data.length); i++) {
                    data[i] = byteText[i];
                }
                Log.d(TAG, "bytes = " + MifareUltralightProcessor.toString(MifareUltralightProcessor.toObjects(data)));
                Log.d(TAG, "Calculation pages...");
                Log.d(TAG, "--------------");
                for (int page = begin; page <= end; page++) {
                    Log.d(TAG, "PAGE = " + page);
                    byte[] pageData = new byte[4];
                    int offset = (page - begin) * 4;
                    Log.d(TAG, "OFFSET = " + offset);
                    for (int i = 0; i < 4; i++) {
                        pageData[i] = data[offset + i];
                    }
                    Log.d(TAG, "PDATA = " + MifareUltralightProcessor.toString(MifareUltralightProcessor.toObjects(pageData)));
                    Log.d(TAG, "Creating job...");
                    pendingOperations.add(new WriteOperation(pageData, page));
                    Log.d(TAG, "--------------");
                }
            }
        });
        linearLayout.addView(saveButton);
        layout.addView(linearLayout);
        layout.addView(textViewAvailable);
    }
    private void writePending(MifareUltralight mifareUltralight){
        if(pendingOperations == null || pendingOperations.size() == 0)
            return;
        try{
            Log.d(TAG, "Writing pending operations...");
            mifareUltralight.connect();
            for (WriteOperation writeOperation:pendingOperations){
                Log.d(TAG, "Writing: page=" + writeOperation.page + " data="+MifareUltralightProcessor.separateBytes(MifareUltralightProcessor.toBinary(MifareUltralightProcessor.toObjects(writeOperation.data))));
                mifareUltralight.writePage(writeOperation.page, writeOperation.data);
            }
            mifareUltralight.close();
            showText("Записано", R.drawable.ok);
            startWaitingDelayed(2000);
        }
        catch (Exception e){
            showText("Ошибка: " + e.toString(), R.drawable.disabled);
            e.printStackTrace();
            startWaitingDelayed(2000);
        }
    }
    private String getString(ArrayList<Byte> bytes){
        Byte[] array = new Byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            array[i] = bytes.get(i);
        }
        return MifareUltralightProcessor.toString(array);
    }

    class WriteOperation {
        byte[] data;
        int page;

        public WriteOperation(byte[] data, int page) {
            this.data = data;
            this.page = page;
        }
    }
}

