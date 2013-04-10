package com.implix.jsonrpc.observers;

import android.app.Fragment;
import android.os.Bundle;
import android.view.View;

/**
 * Created with IntelliJ IDEA.
 * User: jbogacki
 * Date: 27.02.2013
 * Time: 16:39
 * To change this template use File | Settings | File Templates.
 */
public class ObserverFragment extends Fragment {

    private ObserverHelper observerHelper = new ObserverHelper();

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        observerHelper.start(this, view);
    }

    @Override
    public void onDestroyView() {
        observerHelper.stop();
        super.onDestroyView();
    }
}
