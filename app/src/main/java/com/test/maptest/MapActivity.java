package com.test.maptest;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.services.android.ui.geocoder.GeocoderAutoCompleteView;
import com.mapbox.services.api.geocoding.v5.GeocodingCriteria;
import com.mapbox.services.api.geocoding.v5.MapboxGeocoding;
import com.mapbox.services.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.services.api.geocoding.v5.models.GeocodingResponse;
import com.mapbox.services.commons.models.Position;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by bguedon on 24/10/2017.
 */

public class MapActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "com.test.MapTest";

    private static final String SP_KEY_ADDRESS_HISTORY = "com.test.MapTest.AddressHistory";

    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;
    GeocoderAutoCompleteView autocomplete;
    MapboxMap.OnCameraIdleListener onCameraIdleListener;
    private MapView mMapView;
    private MapboxMap mMapboxMap;
    private GoogleApiClient mGoogleApiClient;
    private Location mCurrentLocation;

    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;
    private AddressesAdapter addressesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_map);

        // init navigation drawer
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        // Set the adapter for the list view
        addressesAdapter = new AddressesAdapter(this, getAddressHistory());
        mDrawerList.setAdapter(addressesAdapter);
        // Set the list's click listener
        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Address address = ((Address) parent.getAdapter().getItem(position));
                updateCurrentLocation(address.getLatitude(),address.getLongitude());
                updateMapCenter();
                mDrawerLayout.closeDrawers();
            }
        });


        // Create an instance of GoogleAPIClient.
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        mMapView = (MapView) findViewById(R.id.mapView);
        mMapView.onCreate(savedInstanceState);
        onCameraIdleListener = new MapboxMap.OnCameraIdleListener() {
            @Override
            public void onCameraIdle() {
                final MapboxGeocoding reverseGeocode = new MapboxGeocoding.Builder()
                        .setAccessToken(Mapbox.getAccessToken())
                        .setCoordinates(Position.fromCoordinates(mMapboxMap.getCameraPosition().target.getLongitude(), mMapboxMap.getCameraPosition().target.getLatitude()))
                        .setGeocodingType(GeocodingCriteria.TYPE_ADDRESS)
                        .build();
                reverseGeocode.enqueueCall(new Callback<GeocodingResponse>() {
                    @Override
                    public void onResponse(Call<GeocodingResponse> call, Response<GeocodingResponse> response) {
                        List<CarmenFeature> results = response.body().getFeatures();
                        if (results.size() > 0) {
                            // TODO add parse method and check context size
                            String address = (results.get(0).getAddress() != null ? results.get(0).getAddress() + " " : "") + results.get(0).getText() + " " + results.get(0).getContext().get(0).getText() + " " + results.get(0).getContext().get(1).getText();
                            Log.d(TAG, "onResponse: " + address);
                            autocomplete.setText(address);
                            autocomplete.setSelection(address.length());
                            autocomplete.cancelApiCall();
                            autocomplete.dismissDropDown();
                            hideKeyboard();
                            saveAddressToHistory(new Address(address, results.get(0).getCenter()[1], results.get(0).getCenter()[0]));
                        } else {
                            // No result for your request were found.
                            Log.d(TAG, "onResponse: No result found");
                        }
                    }

                    @Override
                    public void onFailure(Call<GeocodingResponse> call, Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
            }
        };
        mMapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                mMapboxMap = mapboxMap;
                mMapboxMap.addOnCameraIdleListener(onCameraIdleListener);
                mGoogleApiClient.connect();
            }
        });

        // Address auto complete view
        autocomplete = (GeocoderAutoCompleteView) findViewById(R.id.query);
        autocomplete.setAccessToken(Mapbox.getAccessToken());
        autocomplete.setType(GeocodingCriteria.TYPE_ADDRESS);
        autocomplete.setSingleLine(false);
        autocomplete.setMaxLines(3);
        autocomplete.setOnFeatureListener(new GeocoderAutoCompleteView.OnFeatureListener() {
            @Override
            public void onFeatureClick(CarmenFeature feature) {
                hideKeyboard();
                Position position = feature.asPosition();
                updateCurrentLocation(position.getLatitude(), position.getLongitude());
                updateMapCenter();
            }
        });

    }

    @SuppressWarnings({"MissingPermission"})
    private void updateLastknownLocation() {
        mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
    }

    private void updateCurrentLocation(double latitude, double longitude) {
        mCurrentLocation.reset();
        mCurrentLocation.setLatitude(latitude);
        mCurrentLocation.setLongitude(longitude);
    }

    private void updateMapCenter() {
        if (mCurrentLocation != null) {
            mMapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mCurrentLocation), 16));
            mMapboxMap.setZoom(16);
        }
    }

    private ArrayList<Address> getAddressHistory() {

        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        String addresses = sharedPreferences.getString(SP_KEY_ADDRESS_HISTORY, null);
        Type addressListType = new TypeToken<ArrayList<Address>>() {
        }.getType();
        if (addresses != null) {
            return new Gson().fromJson(addresses, addressListType);
        } else {
            return new ArrayList<>();
        }
    }

    private void saveAddressToHistory(Address address) {
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        String addresses = sharedPreferences.getString(SP_KEY_ADDRESS_HISTORY, null);
        ArrayList<Address> addressList;
        Type addressListType = new TypeToken<ArrayList<Address>>() {
        }.getType();

        if (addresses == null) {
            addressList = new ArrayList<Address>();
        } else {
            addressList = new Gson().fromJson(addresses, addressListType);
        }

        // discard already stored address, same latlong
        for (int i = 0; i < addressList.size(); i++) {
            Address item = addressList.get(i);
            if (item.getLatitude() == address.getLatitude() && item.getLongitude() == address.getLongitude()) {
                addressList.remove(i);
                break;
            }
        }
        // add item
        addressList.add(0, address);
        // keep 15 items max
        if (addressList.size() > 15) {
            addressList = new ArrayList<>(addressList.subList(0, 15));
        }
        // store
        sharedPreferences.edit().putString(SP_KEY_ADDRESS_HISTORY, new Gson().toJson(addressList, addressListType)).commit();
        Log.d(TAG, "saved address, total " + addressList.size() + "\n" + address);

        // refresh list adapter
        addressesAdapter.clear();
        addressesAdapter.addAll(addressList);
        addressesAdapter.notifyDataSetChanged();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    updateLastknownLocation();
                    updateMapCenter();

                } else {

                    // permission denied
                    Toast.makeText(this, R.string.permissions_missing_fine_location, Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mMapView.onStart();
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
        mMapView.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
    }

    // google api client callbacks
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            updateLastknownLocation();
            updateMapCenter();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
