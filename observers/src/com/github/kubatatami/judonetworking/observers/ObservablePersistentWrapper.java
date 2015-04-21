package com.github.kubatatami.judonetworking.observers;

import android.content.Context;
import android.support.annotation.NonNull;

import com.github.kubatatami.judonetworking.internals.stats.MethodStat;
import com.github.kubatatami.judonetworking.logs.JudoLogger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Kuba on 18/04/15.
 */
public class ObservablePersistentWrapper<T extends Serializable> extends ObservableWrapper<T> {

    protected static Level defaultLevel = Level.DATA;


    protected static LinkedBlockingDeque<Runnable> queue = new LinkedBlockingDeque<>();

    protected static Executor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue, new ThreadFactory() {
        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }
    });


    protected Context context;
    protected String persistentKey;
    protected Level level = defaultLevel;
    protected boolean loaded;
    protected Semaphore semaphore = new Semaphore(1, true);
    protected Runnable loadingRunnable = new Runnable() {
        @Override
        public void run() {
            loadDataSync();
        }
    };

    public ObservablePersistentWrapper(Context context, String persistentKey) {
        this.context = context;
        this.persistentKey = persistentKey;
        loadDataAsync();
    }

    public ObservablePersistentWrapper(Context context, String persistentKey, Level level) {
        this.context = context;
        this.persistentKey = persistentKey;
        this.level = level;
        loadDataAsync();
    }

    protected static File getPersistentDir(Level level, Context context) {
        File dir = new File((level.equals(Level.CACHE) ? context.getCacheDir() : context.getFilesDir()) + "/ObservablePersistentWrapper/");
        dir.mkdirs();
        return dir;
    }

    protected File getPersistentFile() {
        return new File(getPersistentDir(level, context), persistentKey);
    }

    @Override
    public synchronized T get() {
        try {
            long time = System.currentTimeMillis();
            if (queue.contains(loadingRunnable)) {
                queue.removeFirstOccurrence(loadingRunnable);
                queue.addFirst(loadingRunnable);
            }
            semaphore.acquire();
            T result = super.get();
            semaphore.release();
            time = System.currentTimeMillis() - time;
            if (time > 0) {
                JudoLogger.log("ObservablePersistentWrapper " + persistentKey + " waiting:" + time + "ms");
            }
            return result;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected void loadDataSync() {
        InputStream fileStream = null;
        ObjectInputStream os = null;
        File file = getPersistentFile();
        if (file.exists()) {
            try {
                fileStream = new BufferedInputStream(new FileInputStream(getPersistentFile()));
                os = new ObjectInputStream(fileStream);
                PersistentData<T> persistentData = (PersistentData<T>) os.readObject();
                set(persistentData.object, true, persistentData.dataSetTime);
            } catch (Exception e) {
                JudoLogger.log(e);
            } finally {
                try {
                    if (os != null) {
                        os.close();
                    }if (fileStream != null) {
                        fileStream.close();
                    }
                } catch (IOException ex) {
                    JudoLogger.log(ex);
                }
            }
        }
        loaded=true;
        semaphore.release();
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    protected void loadDataAsync() {
        try {
            semaphore.acquire();
            executor.execute(loadingRunnable);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void saveData(final T object) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                OutputStream fileStream = null;
                ObjectOutputStream os = null;
                try {
                    fileStream =new BufferedOutputStream( new FileOutputStream(getPersistentFile()));
                    os = new ObjectOutputStream(fileStream);
                    os.writeObject(new PersistentData<>(dataSetTime, object));
                    os.flush();
                } catch (IOException e) {
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
        });
    }

    @Override
    public boolean set(T object, boolean notify) {
        boolean result = super.set(object, notify);
        saveData(object);
        return result;
    }

    public static void removeAllDataSync(Context context) {
        removeAllDataSync(defaultLevel, context);
    }

    public static void removeAllDataAsync(final Context context) {
        removeAllDataAsync(defaultLevel, context);
    }

    public static void removeAllDataSync(Level level, Context context) {
        for (File file : getPersistentDir(level, context).listFiles()) file.delete();
    }

    public static void removeAllDataAsync(final Level level, final Context context) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                removeAllDataSync(level, context);
            }
        });
    }

    protected enum Level {
        CACHE, DATA
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
