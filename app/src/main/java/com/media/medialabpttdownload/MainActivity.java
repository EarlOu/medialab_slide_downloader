package com.media.medialabpttdownload;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends ListActivity {
    private static final String TAG = "MainActivity";

    private static final String PPT_URL =
            "http://media.ee.ntu.edu.tw/group_meeting_ppt/test_php/searchPPTu.php";

    private static final String PREF = "pref";
    private static final String PREF_USERNAME = "username";
    private static final String PREF_PASSWORD = "password";

    private static final int REQUEST_TIMEOUT = 10000;

    private static final DefaultHttpClient mClient;
    static {
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, REQUEST_TIMEOUT);
        HttpConnectionParams.setSoTimeout(httpParams, REQUEST_TIMEOUT);
        mClient = new DefaultHttpClient(httpParams);
    }

    private Dialog mLoginDialog;
    private Dialog mRetryDialog;
    private Dialog mSearchDialog;
    private String mUsername = "";
    private String mPassword = "";
    private String mPresenter = "*";
    private int mType = 0;
    private String mTitle = "";
    private long mDateFrom = System.currentTimeMillis() - 31536000000L;
    private long mDateTo = System.currentTimeMillis();
    private String mKeyword = "";
    private int mPageSize = 10;
    private String mDownloadLink;
    private List<Item> mItems = new ArrayList<Item>();

    private String[] mPresentType;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        mPresentType = getResources().getStringArray(R.array.present_types);
        buildLoginDialog();
        mLoginDialog.show();
    }

    private void buildLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.login_dialog_title));
        LayoutInflater inflater =
                (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.login_dialog, null);
        builder.setView(view);
        final EditText username = (EditText) view.findViewById(R.id.username);
        final EditText password = (EditText) view.findViewById(R.id.password);
        final SharedPreferences pref = getSharedPreferences(PREF, 0);
        username.setText(pref.getString(PREF_USERNAME, ""));
        password.setText(pref.getString(PREF_PASSWORD, ""));
        builder.setPositiveButton(getResources().getString(R.string.login)
                , new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mUsername = username.getText().toString();
                mPassword = password.getText().toString();
                SharedPreferences.Editor editor = pref.edit();
                editor.putString(PREF_USERNAME, mUsername);
                editor.putString(PREF_PASSWORD, mPassword);
                editor.commit();
                query();
            }
        });
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        });
        mLoginDialog = builder.create();
    }

    private static final String ENCODING_TYPE = "utf-8";
    private static final String DATETIME_FORMAT = "yyyy-MM-dd";
    private static final SimpleDateFormat sFormater = new SimpleDateFormat(DATETIME_FORMAT);

    private void query() {
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("presenterPat", mPresenter));
        nameValuePairs.add(new BasicNameValuePair("typePat",
                (mType == 0) ? "*" : mPresentType[mType]));
        nameValuePairs.add(new BasicNameValuePair("titlePat", mTitle));
        nameValuePairs.add(new BasicNameValuePair("datefrom", sFormater.format(mDateFrom)));
        nameValuePairs.add(new BasicNameValuePair("dateto", sFormater.format(mDateTo)));
        nameValuePairs.add(new BasicNameValuePair("semfrom", sFormater.format(mDateFrom)));
        nameValuePairs.add(new BasicNameValuePair("semto", sFormater.format(mDateTo)));
        nameValuePairs.add(new BasicNameValuePair("keywordPat", mKeyword));
        nameValuePairs.add(new BasicNameValuePair("pageSize", String.valueOf(mPageSize)));
        nameValuePairs.add(new BasicNameValuePair("startIndex",String.valueOf(0)));
        String url = PPT_URL + "?" + URLEncodedUtils.format(nameValuePairs, ENCODING_TYPE);
        final HttpGet httpGet = new HttpGet(url);

        new AsyncTask<Void, Void, Integer>() {
            private ProgressDialog progress;
            private String resBody;
            @Override
            protected void onPreExecute() {
                progress = ProgressDialog.show(MainActivity.this
                        , "", "Wait for media.ee.ntu.edu.tw");
                progress.setCancelable(true);
                progress.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        cancel(true);
                    }
                });
            }

            @Override
            protected Integer doInBackground(Void... params) {
                try {
                    Credentials creds = new UsernamePasswordCredentials(
                            mUsername, mPassword);
                    Header header = new BasicScheme().authenticate(creds, httpGet);
                    httpGet.setHeader(header);
                    HttpResponse response = mClient.execute(httpGet);
                    resBody = EntityUtils.toString(response.getEntity(), ENCODING_TYPE);
                    return response.getStatusLine().getStatusCode();
                } catch (AuthenticationException e) {
                    Log.e(TAG, "",e);
                } catch (ClientProtocolException e) {
                    Log.e(TAG, "",e);
                } catch (IOException e) {
                    Log.e(TAG, "",e);
                }
                return 0;
            }

            @Override
            protected void onPostExecute(Integer code) {
                progress.dismiss();
                if (code == 200) {
                    handleQueryResponse(resBody);
                } else if (code == 401) {
                    mLoginDialog.show();
                } else {
                    showRetryDialog();
                }
            }
        }.execute();
    }

    private void handleQueryResponse(String resBody) {
        mItems.clear();
        Document doc = Jsoup.parse(resBody);
        Element table = doc.select("tbody").first();
        Elements list = table.children();
        for (int i = 0, n = list.size(); i < n; i++) {
            if(i % 2 == 0) continue;
            Element element = list.get(i);
            Elements data = element.children();
            Item item = new Item();
            item.time = data.get(0).text();
            item.type = data.get(1).text();
            item.title = data.get(2).child(0).text();
            item.presenter = data.get(3).text();
            item.link = data.get(4).child(0).attr("href");
            mItems.add(item);
        }
        setListAdapter(new MyAdapter(this, 0, mItems));
    }

    private void showRetryDialog() {
        if (mRetryDialog != null) {
            mRetryDialog.show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getResources().getString(R.string.network_error));
        builder.setPositiveButton(R.string.retry, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int button) {
                query();
            }
        });
        builder.setCancelable(true);
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        });
        mRetryDialog = builder.create();
        mRetryDialog.show();
    }

    // Call when click search btn
    public void search(View btn) {
        if (mSearchDialog != null) {
            mSearchDialog.show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.search);
        LayoutInflater inflater =
                (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.query_dialog, null);
        builder.setView(view);
        final EditText reporter = (EditText) view.findViewById(R.id.reporter);
        final Spinner type = (Spinner) view.findViewById(R.id.type);
        final EditText title = (EditText) view.findViewById(R.id.title);
        final DatePicker from = (DatePicker) view.findViewById(R.id.from);
        final DatePicker to = (DatePicker) view.findViewById(R.id.to);
        final EditText keyword = (EditText) view.findViewById(R.id.keyword);
        final EditText numOfResult = (EditText) view.findViewById(R.id.number_of_result);
        reporter.setText(mPresenter);
        type.setAdapter(
                new ArrayAdapter<String> (this,
                        android.R.layout.simple_spinner_item, mPresentType));
        title.setText(mTitle);
        Date fromDate = new Date(mDateFrom);
        Date toDate = new Date(mDateTo);
        from.updateDate(fromDate.getYear() + 1900, fromDate.getMonth(), fromDate.getDate());
        to.updateDate(toDate.getYear() + 1900, toDate.getMonth(), toDate.getDate());
        keyword.setText(mKeyword);
        numOfResult.setText(String.valueOf(mPageSize));

        builder.setPositiveButton(R.string.submit, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int button) {
                mPresenter = reporter.getText().toString();
                mPresenter = (mPresenter.length() == 0) ? "*" : mPresenter;
                mType = type.getSelectedItemPosition();
                mTitle = title.getText().toString();
                mDateFrom = convertToUnixTime(from);
                mDateTo = convertToUnixTime(to);
                mKeyword = keyword.getText().toString();
                mPageSize = Integer.valueOf(numOfResult.getText().toString());
                query();
            }
        });

        builder.setCancelable(true);
        mSearchDialog = builder.create();
        mSearchDialog.show();
    }

    static private long convertToUnixTime(DatePicker picker) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, picker.getYear());
        c.set(Calendar.MONTH, picker.getMonth());
        c.set(Calendar.DAY_OF_MONTH, picker.getDayOfMonth());
        c.set(Calendar.HOUR, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static class Item {
        public String time;
        public String type;
        public String title;
        public String presenter;
        public String link;
    }

    private class MyAdapter extends ArrayAdapter<Item> {
        public MyAdapter(Context context, int textViewResourceId,
                List<Item> objects) {
            super(context, textViewResourceId, objects);
        }

        private static final String TYPE = "Type: ";
        private static final String PRESENTER = "Presenter: ";
        private static final String DATE = "Date: ";
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater =
                        (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                View view = inflater.inflate(R.layout.item, null);
                view.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
                        LayoutParams.WRAP_CONTENT));
                Item item = getItem(position);
                TextView titleView = (TextView) view.findViewById(R.id.title);
                TextView typeView = (TextView) view.findViewById(R.id.type);
                TextView presenterView = (TextView) view.findViewById(R.id.presenter);
                TextView dateView = (TextView) view.findViewById(R.id.date);
                titleView.setText(item.title);
                typeView.setText(TYPE + item.type);
                presenterView.setText(PRESENTER + item.presenter);
                dateView.setText(DATE + item.time);
                convertView = view;
            }
            return convertView;
        }
    }

    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);
        mDownloadLink = mItems.get(position).link;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            showDownloadDialog();
        } else {
            Toast.makeText(this, R.string.storage_not_available, Toast.LENGTH_LONG).show();
        }
    }

    private void showDownloadDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.download_to);
        LayoutInflater inflater =
                (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.download_dialog, null);
        builder.setView(view);
        final EditText pathView = (EditText) view.findViewById(R.id.path);
        String path = Environment.getExternalStorageDirectory().getPath() + "/"
                + Uri.parse(mDownloadLink).getLastPathSegment();
        pathView.setText(path);
        builder.setPositiveButton(R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int btn) {
                download(mDownloadLink, pathView.getText().toString());
            }
        });
        builder.setCancelable(true);
        builder.create().show();
    }

    private void download(final String url, final String path) {
        new AsyncTask<Void, Integer, Boolean> () {
            ProgressDialog dialog;
            @Override
            public void onPreExecute() {
                dialog = new ProgressDialog(MainActivity.this);
                dialog.setMessage(getResources().getString(R.string.downloading));
                dialog.setIndeterminate(false);
                dialog.setMax(100);
                dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                dialog.setCancelable(true);
                dialog.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        cancel(true);
                        new File(path).delete();
                    }
                });
                dialog.show();
            }

            @Override
            public void onProgressUpdate(Integer... value) {
                super.onProgressUpdate(value);
                dialog.setProgress(value[0]);
            }

            @Override
            public void onPostExecute(Boolean success) {
                dialog.dismiss();
                if (success) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    File file = new File(path);
                    String filenameArray[] = path.split("\\.");
                    String extension = filenameArray[filenameArray.length-1];
                    String mimeType = MimeTypeMap.getSingleton().
                            getMimeTypeFromExtension(extension);
                    intent.setDataAndType(Uri.fromFile(file), mimeType);
                    PackageManager packageManager = getPackageManager();
                    List<ResolveInfo> intentList = packageManager.queryIntentActivities(
                            intent, PackageManager.MATCH_DEFAULT_ONLY);
                    if (intentList.size() == 0) {
                        Toast.makeText(MainActivity.this, R.string.no_reader
                                , Toast.LENGTH_LONG).show();
                    } else {
                        startActivity(intent);
                    }
                } else {
                    Toast.makeText(MainActivity.this, R.string.error_when_download
                            , Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public Boolean doInBackground(Void... params) {
                try {
                    Uri u = Uri.parse(url);
                    String noLastSegment = u.getScheme() + "://" + u.getAuthority() + "/";
                    for(int i = 0, n = u.getPathSegments().size() - 1; i < n; i++) {
                       noLastSegment += u.getPathSegments().get(i) + "/";
                    }
                    noLastSegment += Uri.encode(Uri.parse(url).getLastPathSegment()
                            , ENCODING_TYPE);

                    HttpGet httpGet = new HttpGet(noLastSegment);
                    Credentials creds = new UsernamePasswordCredentials(
                            mUsername, mPassword);
                    Header header = new BasicScheme().authenticate(creds, httpGet);
                    httpGet.setHeader(header);
                    HttpResponse response = mClient.execute(httpGet);
                    if (response.getStatusLine().getStatusCode() != 200) {
                        return false;
                    }
                    long length = response.getEntity().getContentLength();
                    InputStream is = response.getEntity().getContent();
                    FileOutputStream os = new FileOutputStream(new File(path));
                    byte[] buf = new byte[1024];
                    int n = 0;
                    double dataDownloaded = 0;
                    while ((n = is.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        dataDownloaded += n;
                        publishProgress((int) (dataDownloaded / length * 100));
                    }
                    os.close();
                    return true;
                } catch (ClientProtocolException e) {
                    Log.e(TAG, "", e);
                } catch (IOException e) {
                    Log.e(TAG, "", e);
                } catch (AuthenticationException e) {
                    Log.e(TAG, "", e);
                }
                return false;
            }
        }.execute();
    }
}
