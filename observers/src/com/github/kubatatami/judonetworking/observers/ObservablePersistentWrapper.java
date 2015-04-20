package com.github.kubatatami.judonetworking.observers;

import android.content.Context;
import android.support.annotation.NonNull;

import com.github.kubatatami.judonetworking.internals.stats.MethodStat;
import com.github.kubatatami.judonetworking.logs.JudoLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Created by Kuba on 18/04/15.
 */
public class ObservablePersistentWrapper<T extends Serializable> extends ObservableWrapper<T> {

    protected Context context;
    protected String persistentKey;
    protected boolean loadAsync=false;

    protected static Executor executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }
    });


    public ObservablePersistentWrapper(Context context, String persistentKey) {
        this.context = context.getApplicationContext();
        this.persistentKey = persistentKey;
        loadData();
    }

    public ObservablePersistentWrapper(boolean notifyInUiThread, Context context, String persistentKey) {
        super(notifyInUiThread);
        this.context = context.getApplicationContext();
        this.persistentKey = persistentKey;
        loadData();
    }

    public ObservablePersistentWrapper(long updateTime, Context context, String persistentKey) {
        super(updateTime);
        this.context = context.getApplicationContext();
        this.persistentKey = persistentKey;
        loadData();
    }

    public ObservablePersistentWrapper(boolean notifyInUiThread, long updateTime, Context context, String persistentKey) {
        super(notifyInUiThread, updateTime);
        this.context = context.getApplicationContext();
        this.persistentKey = persistentKey;
        loadData();
    }

    public ObservablePersistentWrapper(boolean notifyInUiThread, boolean allowNull, Context context, String persistentKey) {
        super(notifyInUiThread, allowNull);
        this.context = context.getApplicationContext();
        this.persistentKey = persistentKey;
        loadData();
    }

    public ObservablePersistentWrapper(long updateTime, boolean allowNull, Context context, String persistentKey) {
        super(updateTime, allowNull);
        this.context = context.getApplicationContext();
        this.persistentKey = persistentKey;
        loadData();
    }

    public ObservablePersistentWrapper(boolean notifyInUiThread, long updateTime, boolean allowNull, Context context, String persistentKey) {
        super(notifyInUiThread, updateTime, allowNull);
        this.context = context.getApplicationContext();
        this.persistentKey = persistentKey;
        loadData();
    }

    public ObservablePersistentWrapper(String persistentKey, boolean loadAsync) {
        this.persistentKey = persistentKey;
        this.loadAsync = loadAsync;
        loadData();
    }

    public ObservablePersistentWrapper(boolean notifyInUiThread, String persistentKey, boolean loadAsync) {
        super(notifyInUiThread);
        this.persistentKey = persistentKey;
        this.loadAsync = loadAsync;
        loadData();
    }

    public ObservablePersistentWrapper(long updateTime, String persistentKey, boolean loadAsync) {
        super(updateTime);
        this.persistentKey = persistentKey;
        this.loadAsync = loadAsync;
        loadData();
    }

    public ObservablePersistentWrapper(boolean notifyInUiThread, long updateTime, String persistentKey, boolean loadAsync) {
        super(notifyInUiThread, updateTime);
        this.persistentKey = persistentKey;
        this.loadAsync = loadAsync;
        loadData();
    }

    public ObservablePersistentWrapper(boolean notifyInUiThread, boolean allowNull, String persistentKey, boolean loadAsync) {
        super(notifyInUiThread, allowNull);
        this.persistentKey = persistentKey;
        this.loadAsync = loadAsync;
        loadData();
    }

    public ObservablePersistentWrapper(long updateTime, boolean allowNull, String persistentKey, boolean loadAsync) {
        super(updateTime, allowNull);
        this.persistentKey = persistentKey;
        this.loadAsync = loadAsync;
        loadData();
    }

    public ObservablePersistentWrapper(boolean notifyInUiThread, long updateTime, boolean allowNull, String persistentKey, boolean loadAsync) {
        super(notifyInUiThread, updateTime, allowNull);
        this.persistentKey = persistentKey;
        this.loadAsync = loadAsync;
        loadData();
    }

    protected static File getPersistentDir(Context context) {
        File dir = new File(context.getCacheDir() + "/ObservablePersistentWrapper/");
        dir.mkdirs();
        return dir;
    }

    protected File getPersistentFile() {
        return new File(getPersistentDir(context), persistentKey);
    }

    protected void loadData() {
        if(loadAsync){
            loadDataAsync();
        }else{
            loadDataSync();
        }
    }

    protected void loadDataSync() {
        FileInputStream fileStream = null;
        ObjectInputStream os = null;
        try {
            fileStream = new FileInputStream(getPersistentFile());
            os = new ObjectInputStream(fileStream);
            PersistentData<T> persistentData = (PersistentData<T>) os.readObject();
            set(persistentData.object, true, persistentData.dataSetTime);
        } catch (Exception e) {
            JudoLogger.log(e);
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                if (fileStream != null) {
                    fileStream.close();
                }
            } catch (IOException ex) {
                JudoLogger.log(ex);
            }
        }
    }

    protected void loadDataAsync() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                loadDataSync();
            }
        });
    }

    protected void saveData(final T object) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(getPersistentFile()));
                    os.writeObject(new PersistentData<>(updateTime, object));
                    os.flush();
                    os.close();
                } catch (IOException e) {
                    JudoLogger.log(e);
                }
            }
        });
    }

    @Override
    public boolean set(T object, boolean notify) {
        boolean result = super.set(object, notify);
        saveData(object);
        return result;
    }

    public static void removeAllDataSync(Context context){
        for(File file: getPersistentDir(context).listFiles()) file.delete();
    }

    public static void removeAllDataAsync(final Context context) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                removeAllDataSync(context);
            }
        });
    }

    protected static class PersistentData<T> implements Serializable {
        public long dataSetTime;
        public T object;

        public PersistentData() {
        }

        public PersistentData(long dataSetTime, T object) {
            this.dataSetTime = dataSetTime;
            this.object = object;
        }
    }
}
