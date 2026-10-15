package br.com.pmcn.transcritor;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
public class TestAudioProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    public String getType(Uri uri) { return "audio/ogg"; }
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = new File(getContext().getCacheDir(), "shared-test.opus");
        try (InputStream input = getContext().getAssets().open("teste.opus"); OutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) >= 0) out.write(buffer, 0, n);
        } catch (IOException e) { throw new FileNotFoundException(e.toString()); }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    public Uri insert(Uri u, ContentValues v) { return null; }
    public int delete(Uri u, String s, String[] a) { return 0; }
    public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
