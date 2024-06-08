package com.rasamadev.varsign.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.rasamadev.varsign.R;

import java.util.Vector;

import de.tsenger.androsmex.data.CANSpecDO;
import de.tsenger.androsmex.data.CANSpecDOStore;

public class AdapterCanList extends ArrayAdapter<CANSpecDO> {
    private Vector<CANSpecDO> items;
    private final LayoutInflater vi;
    private final ListView parentView;
    private CANSpecDOStore canstore;


    public AdapterCanList(Context context, ListView parent, CANSpecDOStore _canStore) {
        super(context,0, _canStore.getAll());
        this.items = _canStore.getAll();
        parentView = parent;
        vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        canstore = _canStore;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;

        final CANSpecDO ei = items.get(position);
        if (ei != null)
        {
            v = vi.inflate(R.layout.list_mrtd_row, null);
            final TextView title = (TextView)v.findViewById(R.id.row_can);
            final TextView name = (TextView)v.findViewById(R.id.row_name);
            final TextView nif = (TextView)v.findViewById(R.id.row_nif);

            if(title != null) {
                title.setText(ei.getCanNumber());
            }
            if(name != null && !ei.getUserName().isEmpty() ) {
                name.setText(ei.getUserName());
            }
            if(nif != null && !ei.getUserNif().isEmpty()) {
                nif.setText("DNI " + ei.getUserNif());
            }

            Button deleteImageView = (Button)  v.findViewById(R.id.Btn_DESTROYENTRY);
            deleteImageView.setOnClickListener(v1 -> {
                RelativeLayout vwParentRow = (RelativeLayout) v1.getParent();
                int position1 = parentView.getPositionForView(vwParentRow);
                canstore.delete(items.get(position1));
                AdapterCanList.this.remove(items.get(position1));
                items = canstore.getAll();
            });
        }
        return v;
    }
}
