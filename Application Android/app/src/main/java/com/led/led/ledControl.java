package com.led.led;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class ledControl extends ActionBarActivity {

    Button btnOn, btnOff, btnDis, btnIntensite;
    EditText numIntensite;
    SeekBar brightness;

    TextView lumn;
    TextView labelDistance;

    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    //SPP UUID. Look for it
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Intensité sur 100, a convertir sur 255 dans l'arduino
    float intensite;

    // Pour GPS
    Location maLocation;
    float distance;

    Location POI;
    Location gareBgx;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Intent newint = getIntent();
        address = newint.getStringExtra(DeviceList.EXTRA_ADDRESS); //receive the address of the bluetooth device

        //view of the ledControl
        setContentView(R.layout.activity_led_control);


        btnDis = (Button)findViewById(R.id.button4);
        lumn = (TextView)findViewById(R.id.lumn);

        labelDistance = (TextView)findViewById(R.id.labelDistance);

        new ConnectBT().execute(); //Call the class to connect

        // Pour le GPS
        LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        ArrayList<LocationProvider> providers = new ArrayList<LocationProvider>();
        List<String> names = locationManager.getProviders(true);

        ArrayList<Location> servicesPublic = new ArrayList<Location>();


        for(String name : names)
            providers.add(locationManager.getProvider(name));


        POI=new Location("POI");

        // Coordonnées du point de test
        POI.setLatitude(48.784866);
        POI.setLongitude(2.315309);


        // AMFA     48.786430, 2.299051
        //POI.setLatitude(48.786430);
        //POI.setLongitude(2.299051);


        gareBgx=new Location("gareBgx");
        gareBgx.setLatitude(48.7863538);
        gareBgx.setLongitude(2.3144456);

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 5, new LocationListener() {

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }

            @Override
            public void onLocationChanged(Location location) {
                maLocation = location;

                // On définit le rayon d'allumage en mètres
                int distanceMin = 80;

                // On affiche dans le log la position
                Log.d("GPS", "Latitude " + location.getLatitude() + " et longitude " + location.getLongitude());

                distance = location.distanceTo(POI); // en mètres

                // On affiche dans le log la distance au POI le plus proche
                Log.d("GPS", "distance mairie :" + distance);

                // On met à jour le label qui affiche la distance du point d'interet le plus proche
                labelDistance.setText(Float.toString(distance));

                // Si l'on rentre dans le rayon d'allumage
                // L'intensité est sur 100 pour pouvoir vérifier facilement le nombre de Bits recus dans la l'arduino
                if (distance < distanceMin) {
                    intensite = ((distanceMin - distance) / distanceMin) * 100;

                    // On arrondit l'intensite
                    intensite = Math.round(intensite);
                    Log.d("GPS", "Intensite : " + intensite);

                    try {
                        btSocket.getOutputStream().write(Float.toString(intensite).getBytes());
                        Log.d("GPS", "envoi de l'intensité par bluetooth réussi. ");

                    } catch (IOException e) {
                        Log.d("GPS", "Echec de l'envoie de l'intenstié par bluetooth ");

                    }
                }

            }
        });

        // fin GPS


        btnDis.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Disconnect(); //close connection
            }
        });
    }

    private void Disconnect()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try
            {
                // On etteind les LED
                btSocket.getOutputStream().write(Float.toString(0).getBytes());
                btSocket.close(); //close connection
            }
            catch (IOException e)
            { msg("Erreur.");}
        }
        finish(); //return to the first layout
    }

    // fast way to call Toast
    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_led_control, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(ledControl.this, "Connexion en cours ...", "Patience ...");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                 myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                 BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                 btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                 BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                 btSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                msg("La connexion a échoué. Est-ce que c'est un appareil bluetooth SPP ? Essayez encore.");
                finish();
            }
            else
            {
                msg("Bravo, vous êtes connectés !");
                isBtConnected = true;
            }
            progress.dismiss();
        }
    }
}
