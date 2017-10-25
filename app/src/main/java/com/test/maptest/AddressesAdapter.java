package com.test.maptest;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by bguedon on 25/10/2017.
 */

public class AddressesAdapter extends ArrayAdapter<Address> {

    public AddressesAdapter(@NonNull Context context, ArrayList<Address> addresses) {
        super(context, 0, addresses);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the address
        Address address = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.address_list_item, parent, false);
        }
        TextView tvAddress = (TextView) convertView.findViewById(R.id.addresstext);
        tvAddress.setText(address.getText());
        return convertView;
    }

}
