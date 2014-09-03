package burton.michael.psidownloader;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by michael on 9/2/14.
 */
public class ActivityMain extends ActionBarActivity {

    private static final String TAG = ActivityMain.class.getSimpleName();

    private Context mContext;

    private EditText mInputUrl;
    private Button mButtonDownload;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;

        setContentView(R.layout.main);

        // if we're being restored from a previous state,
        // then we don't need to do anything and should return
        if (savedInstanceState != null)
            return;


        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
            actionBar.setIcon(R.drawable.ic_actionbar);


        mInputUrl = (EditText) findViewById(R.id.input_url);
        mInputUrl.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mButtonDownload.performClick();
                    return true;
                }
                return false;
            }
        });


        mButtonDownload = (Button) findViewById(R.id.button_download);
        mButtonDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mInputUrl.getWindowToken(), 0);

                String url = mInputUrl.getText().toString();

                if(!URLUtil.isValidUrl(url))
                    url = "http://" + url;

                if(Patterns.WEB_URL.matcher(url).matches()) {
                    mInputUrl.setText("");
                    new DownloadUrl(mContext).execute(url);
                } else {
                    Toast.makeText(
                            mContext,
                            getResources().getString(R.string.toast_invalid_url),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /* AsyncTask: Download File from URL */
    private class DownloadUrl extends AsyncTask<String, Void, Boolean> {

        private final int TIMEOUT_CONNECTION = 5000;    // 5 seconds
        private final int TIMEOUT_SOCKET = 30000;       // 30 seconds

        private ProgressDialog mProgressDialog;

        private String mMessage = "";

        public DownloadUrl(Context context) {
            mProgressDialog = new ProgressDialog(context);
        }

        @Override
        protected void onPreExecute() {
            mProgressDialog.setMessage("File download in progress...");
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setCancelable(false);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.show();
        }

        @Override
        public Boolean doInBackground(String... urls) {

            if(!isNetworkOnline()) {
                mMessage = "Unable to connect. Please try again later.";
                return false;
            }

            try { // TODO::THIS IS USED FOR TESTING ONLY::REMOVE FOR PRODUCTION
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            try {
                String f_url = urls[0];

                URL url = new URL(f_url);

                long startTime = System.currentTimeMillis();

                // Open a connection to that URL.
                URLConnection connection = url.openConnection();

                // this timeout affects how long it takes for the app to realize there's a connection problem
                connection.setReadTimeout(TIMEOUT_CONNECTION);
                connection.setConnectTimeout(TIMEOUT_SOCKET);

                // Define InputStreams to read from the URLConnection.
                // uses 3KB download buffer
                InputStream is = connection.getInputStream();
                BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
                String filename = FilenameUtils.getName(f_url);

                File file = new File(path + "/" + filename);

                if(isExternalStorageWritable()) { // verify storage is writable
                    FileOutputStream outStream = new FileOutputStream(file);
                    byte[] buff = new byte[5 * 1024];

                    //Read bytes (and store them) until there is nothing more to read(-1)
                    int len;
                    while ((len = inStream.read(buff)) != -1) {
                        outStream.write(buff, 0, len);
                    }

                    // clean up
                    outStream.flush();
                    outStream.close();
                    inStream.close();

                    Log.i(TAG, "download completed in "
                            + ((System.currentTimeMillis() - startTime) / 1000)
                            + " sec");
                } else {
                    mMessage = "STORAGE IS NOT WRITABLE";
                    return false;
                }
            } catch(MalformedURLException e) {
                mMessage = "FILE DOWNLOAD FAILED";
                e.printStackTrace();
                return false;
            } catch(SocketTimeoutException e) {
                mMessage = "Connection Timeout. Please try again later.";
                e.printStackTrace();
                return false;
            } catch(IOException e) {
                mMessage = "FILE DOWNLOAD FAILED";
                e.printStackTrace();
                return false;
            }

            return true;
        }

        @Override
        public void onPostExecute(Boolean success) {
            if(success && mMessage.equals(""))
                mMessage = "DOWNLOAD FINISHED";

            if(!success && mMessage.equals(""))
                mMessage = "DOWNLOAD FAILED";

            Toast.makeText(mContext, mMessage, Toast.LENGTH_LONG).show();

            if (mProgressDialog.isShowing())
                mProgressDialog.dismiss();
        }
    }

    /* check if network is online (data or wifi) */
    private boolean isNetworkOnline() {
        boolean status = false;

        try{
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getNetworkInfo(0);

            if (netInfo != null && netInfo.getState() == NetworkInfo.State.CONNECTED) {
                status= true;
            } else {
                netInfo = cm.getNetworkInfo(1);

                if(netInfo != null && netInfo.getState() == NetworkInfo.State.CONNECTED)
                    status= true;
            }
        }catch(Exception e){
            e.printStackTrace();
            return false;
        }

        return status;
    }

    /* check if external storage is available for read and write */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
}
