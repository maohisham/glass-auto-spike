package com.glass.headunit;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/** Lets the user pick a JAKIM prayer zone by hand, or go back to following the GPS. */
public class ZonePickerActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final List<Zones.Zone> zones = Zones.all();
        List<String> rows = new ArrayList<>();
        rows.add("Automatic (follow the GPS)");
        for (Zones.Zone z : zones) rows.add(z.code + "  " + z.name);
        ListView list = new ListView(this);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
        list.setOnItemClickListener((parent, view, position, id) -> {
            Settings.of(this).setManualZone(position == 0 ? "" : zones.get(position - 1).code);
            Spike.log("settings: prayer zone " + (position == 0 ? "automatic" : zones.get(position - 1).code));
            finish();
        });
        setContentView(list);
        setTitle("Prayer zone");
    }
}
