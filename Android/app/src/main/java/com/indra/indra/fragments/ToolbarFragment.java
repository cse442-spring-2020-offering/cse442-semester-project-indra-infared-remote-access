package com.indra.indra.fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.github.nkzawa.emitter.Emitter;
import com.google.android.material.appbar.AppBarLayout;
import com.indra.indra.MainActivity;
import com.indra.indra.R;
import com.indra.indra.objects.RemoteConfig;
import com.indra.indra.objects.RemoteConfigListAdapter;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class ToolbarFragment extends Fragment {

    private static final String TAG = "ToolbarFragment";
    private static final int STANDARD_APPBAR = 0;
    private static final int SEARCH_APPBAR = 1;
    private int mAppBarState;
    private ListView remotesList;

    private EditText mBrandSearchContacts, mModelSearchContacts;
    private Button submitSearch;

    private AppBarLayout viewContactsBar, searchBar;
    private RemoteConfigListAdapter adapter;
    private View view;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_viewremotes, container, false);
        viewContactsBar = (AppBarLayout) view.findViewById(R.id.viewRemotesToolbar);
        searchBar = (AppBarLayout) view.findViewById(R.id.searchToolbar);
        remotesList = view.findViewById(R.id.remotesList);
        mBrandSearchContacts = view.findViewById(R.id.etBrandSearchContacts);
        mModelSearchContacts = view.findViewById(R.id.etModelSearchContacts);
        submitSearch = view.findViewById(R.id.bSubmitSearch);

        Log.d(TAG, "onCreateView: started");

        setAppBarState(STANDARD_APPBAR);
        ((MainActivity) getActivity()).setMenuItemChecked(R.id.nav_add_device);

        ImageView ivSearchContact = (ImageView) view.findViewById(R.id.ivSearchIcon);
        ivSearchContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: clicked searched icon");
                toggleToolBarState();
            }
        });

        ImageView ivBackArrow = (ImageView) view.findViewById(R.id.ivBackArrow);
        ivBackArrow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: clicked back arrow.");
                toggleToolBarState();
            }
        });

        setupContactList();

        remotesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Get the selected item text from ListView
                RemoteConfig yo = (RemoteConfig) parent.getItemAtPosition(position);
                Log.d(TAG, "listClick: clicked : " + yo.getName());

                //AddDeviceFragment needs
                //Used to switch between fragments in the current activity
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_container, new AddDeviceFragment(yo.getName()));
                ft.addToBackStack(null);
                ft.commit();
            }
        });

        return view;
    }

    // Initiate toggle (it means when you click the search icon it pops up the editText and clicking the back button goes to the search icon again)
    private void toggleToolBarState() {
        Log.d(TAG, "toggleToolBarState: toggling AppBarState.");
        if (mAppBarState == STANDARD_APPBAR) {
            setAppBarState(SEARCH_APPBAR);
        } else {
            setAppBarState(STANDARD_APPBAR);
        }
    }

    // Sets the appbar state for either search mode or standard mode.
    private void setAppBarState(int state) {

        Log.d(TAG, "setAppBaeState: changing app bar state to: " + state);

        mAppBarState = state;
        if (mAppBarState == STANDARD_APPBAR) {
            searchBar.setVisibility(View.GONE);
            viewContactsBar.setVisibility(View.VISIBLE);

            View view = getView();
            InputMethodManager im = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            try {
                im.hideSoftInputFromWindow(view.getWindowToken(), 0); // make keyboard hide
            } catch (NullPointerException e) {
                Log.d(TAG, "setAppBaeState: NullPointerException: " + e);
            }

        } else if (mAppBarState == SEARCH_APPBAR) {
            viewContactsBar.setVisibility(View.GONE);
            searchBar.setVisibility(View.VISIBLE);
            InputMethodManager im = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            im.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0); // make keyboard popup

        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setAppBarState(STANDARD_APPBAR);
    }

    private void setupContactList() {

        submitSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String brandText = mBrandSearchContacts.getText().toString().toLowerCase(Locale.getDefault());
                String modelText = mModelSearchContacts.getText().toString().toLowerCase(Locale.getDefault());
                //Easter Egg code, don't send request to server if key phrase is inputted
                if ((brandText + modelText).equalsIgnoreCase("Matthew Hertz Mode"))
                {
                    ((MainActivity) ToolbarFragment.super.getActivity()).activateEasterEgg();
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.replace(R.id.fragment_container, new MyDevicesFragment());
                    ft.addToBackStack(null);
                    ft.commit();
                }
                else
                {
                    //send search text to server
                    Socket clientSocket = ((MainActivity) getActivity()).getClientSocket();

                    HashMap<String, String> jsonMap = new HashMap<>();
                    jsonMap.put("brand", brandText);
                    jsonMap.put("model", modelText);
                    jsonMap.put("ipAddress", ((MainActivity) getActivity()).getRaspberryPiIP());
                    jsonMap.put("username", ((MainActivity) getActivity()).getCurrentUser());

                    JSONObject message = new JSONObject(jsonMap);
                    clientSocket.emit("search_request", message.toString());
                    Log.d("Search", "Request emitted");

                    //returning object from server

                    clientSocket.on("search_results", new Emitter.Listener() {
                        @Override
                        public void call(Object... args) {
                            Log.d("Search", "Recieved response from server");
                            try {

                                JSONArray ja = (JSONArray) args[0];
                                final ArrayList<RemoteConfig> contacts = new ArrayList<>();
                                for (int i = 0; i < ja.length(); i++) {
                                    JSONObject d = ja.getJSONObject(i);
                                    String b = (String) d.get("brand");
                                    String m = (String) d.get("device");
                                    contacts.add(new RemoteConfig(b + " " + m));
                                }
                                adapter = new RemoteConfigListAdapter(getActivity(), R.layout.layout_remoteconfigs_listitem, contacts, "https://");
                                updateRemoteList();
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                        }
                    });
                    updateRemoteList(); //calls update twice to force UI thread to refresh remote list, avoid explicit multithreading
                    setAppBarState(0);
                }

            }
        });

    }

    /* Asks UI thread to update the remote list after response is successfully found
     *  Without this, handler thread will throw a conflict as it request the same resource as UI thread
     */
    public void updateRemoteList()
    {
        getActivity().runOnUiThread(new Runnable() { //asks UI thread to change UI so handler thread does not conflict
            @Override
            public void run() {
                remotesList.setAdapter(adapter);
            }
        });
    }


}