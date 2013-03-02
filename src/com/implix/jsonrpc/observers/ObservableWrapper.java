package com.implix.jsonrpc.observers;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public class ObservableWrapper<T> {
    private T object = null;
    private Handler handler = new Handler(Looper.getMainLooper());
    private List<WrapObserver<T>> observers = new ArrayList<WrapObserver<T>>();
    private ObservableWrapperListener<T> listener = null;
    private boolean notifyInUiThread = true;
    private long dataSetTime=0;

    public ObservableWrapper() {

    }

    public ObservableWrapper(boolean notifyInUiThread) {
        this.notifyInUiThread = notifyInUiThread;
    }

    public void addObserver(WrapObserver<T> observer) {
        boolean add=true;
        if(listener!=null)
        {
            add=listener.onAddObserver(this,observer);
        }
        if(add)
        {
            observers.add(observer);
            if (object != null) {
                observer.update(object);
            }
        }
    }

    public void deleteObserver(WrapObserver<T> observer) {
        boolean delete=true;
        if(listener!=null)
        {
            delete=listener.onDeleteObserver(this, observer);
        }
        if(delete)
        {
            observers.remove(observer);
        }
    }

    public T get() {
        if(listener!=null)
        {
            listener.onGet(this);
        }
        return object;
    }

    public void set(T object) {
        dataSetTime=System.currentTimeMillis();
        if (object == null) {
            throw new RuntimeException("Do not set null to WrapObserver.");
        }
        this.object = object;
        notifyObservers();
        if(listener!=null)
        {
            listener.onSet(this,object);
        }
    }

    public long getDataSetTime() {
        return dataSetTime;
    }

    public void notifyObservers() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                for (int i = observers.size() - 1; i >= 0; i--) {
                    observers.get(i).update(object);
                }
            }
        };

        if (Looper.getMainLooper().getThread().equals(Thread.currentThread()) || !notifyInUiThread) {
            runnable.run();
        } else {
            handler.post(runnable);
        }

    }

    public void setListener(ObservableWrapperListener<T> listener) {
        this.listener = listener;
    }

    public int getObserversCount()
    {
        return observers.size();
    }
}