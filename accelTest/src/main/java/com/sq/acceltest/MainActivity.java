package com.sq.acceltest;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.WindowManager;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {

    private static TimeSeries xSeries;
    private static TimeSeries zSeries;
    private static TimeSeries axSeries;
    private static TimeSeries axvSeries;
    private static XYMultipleSeriesDataset dataset;
    private static XYMultipleSeriesRenderer renderer;
    private static XYSeriesRenderer rendererSeries1;
    private static XYSeriesRenderer rendererSeries2;
    private static XYSeriesRenderer rendererSeries3;
    private static XYSeriesRenderer rendererSeries4;
    private static GraphicalView view;

    private static SensorManager sensorManager = null;
    Timer timer = new Timer();

    int syncSensorType = 0;
    float[] mGravs = null;
    float[] mRotation = null;

    Date update_time = new Date();
    float x = 0;
    float y = 0;
    float z = 0;
    float prev_x = 0;
    float prev_y = 0;
    float prev_z = 0;
    float ax = 0;
	float ay = 0;
	float az = 0;
    float[] sax = {0.0f, 0.0f, 0.0f, 0.0f};
	float[] say = {0.0f, 0.0f, 0.0f, 0.0f};
	float[] saz = {0.0f, 0.0f, 0.0f, 0.0f};
    float prev_ax = 0;
    float prev_ay = 0;

    float[] axvel = {0.0f, 0.0f, 0.0f}; // {prev_prev, prev, last}
    float axv = 0;
    float ayv = 0;
    float real_axv = 0;
    float real_ayv = 0;

	float accumx = 0;
	float accumy = 0;


    private SensorEventListener sensorEventListener = new SensorEventListener()
    {
        public void onAccuracyChanged(Sensor sensor, int accuracy)
        {
        }

        public void onSensorChanged(SensorEvent event)
        {
            if (event.accuracy < SensorManager.SENSOR_STATUS_UNRELIABLE) return;
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    mGravs = event.values.clone();
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    mRotation = event.values.clone();
                    break;
                default:
                    return;
            }
            // Синхронизация отсылки данных в нативный код с периодичностью опроса одного из существующих датчиков
            if (event.sensor.getType() == syncSensorType) {
                // Используем пока устаревший метод getOrientation вместо getRotation для совместимости
                // со старыми API
                int rot = getWindowManager().getDefaultDisplay().getOrientation();
                if (rot == Surface.ROTATION_0 || rot == Surface.ROTATION_180) {
                    // Дикий хак для устройств, на которых Ladscape это вертикальная ориентация, - меняем местами
                    // значения показателей по осям x и y
                    if (mGravs != null) {
                        float tmp = mGravs[SensorManager.DATA_X];
                        mGravs[SensorManager.DATA_X] = rot == Surface.ROTATION_180 ? -mGravs[SensorManager.DATA_Y] : mGravs[SensorManager.DATA_Y];
                        mGravs[SensorManager.DATA_Y] = rot == Surface.ROTATION_180 ? tmp : -tmp;
                    }
                    if (mRotation != null) {
                        float tmp = mRotation[SensorManager.DATA_X];
                        mRotation[SensorManager.DATA_X] = rot == Surface.ROTATION_180 ? -mRotation[SensorManager.DATA_Y] : mRotation[SensorManager.DATA_Y];
                        mRotation[SensorManager.DATA_Y] = rot == Surface.ROTATION_180 ? tmp : -tmp;
                    }
                } else if (rot == Surface.ROTATION_270) {
                    if (mGravs != null) {
                        mGravs[SensorManager.DATA_X] = -mGravs[SensorManager.DATA_X];
                        mGravs[SensorManager.DATA_Y] = -mGravs[SensorManager.DATA_Y];
                    }
                    if (mRotation != null) {
                        mRotation[SensorManager.DATA_X] = -mRotation[SensorManager.DATA_X];
                        mRotation[SensorManager.DATA_Y] = -mRotation[SensorManager.DATA_Y];
                    }
                }

                if (mGravs != null) {
                    float g_x = mGravs[SensorManager.DATA_X];
                    float g_y = mGravs[SensorManager.DATA_Y];
                    float g_z = mGravs[SensorManager.DATA_Z];

                    x = g_x;
                    y = g_y;
                    z = g_z;
                }
                if (mRotation != null) {
                    final float r_x = mRotation[SensorManager.DATA_X];
                    final float r_y = mRotation[SensorManager.DATA_Y];
                    final float r_z = mRotation[SensorManager.DATA_Z];
                }
            }
        }
    };


    class UpdateTask extends TimerTask
    {
        private float clamp(float value, float min, float max)
        {
            if (value > max) return max;
            else if (value < min) return min;
            else return value;
        }

        private float norm(float x, float y)
        {
            return (float)Math.sqrt(x * x + y * y);
        }

        public void run()
        {
            // Интервал времени между замерами показаний
            Date last_updt = update_time;
            update_time = new Date();
            // Замер проекций осей X и Y и сглаживание
            float d = clamp(Math.abs(norm(prev_x, prev_y) - norm(x, y)) / 0.15f - 1.0f, 0.0f, 1.0f);
            float alpha = (1.0f - d) * 0.5f / 5.0f + d * 0.5f;
            float g = 9.81f;
            x = clamp(x * alpha + prev_x * (1.0f - alpha), -g, g);
            y = clamp(y * alpha + prev_y * (1.0f - alpha), -g, g);
            // Расчет углов поворота осей X и Y
            if (y == 0 && z == 0) ax = 0;
            else ax = (float)Math.atan(x / Math.sqrt(y * y + z * z));
            if (x == 0 && z == 0) ay = 0;
            else ay = (float)Math.atan(y / Math.sqrt(x * x + z * z));
			if (x == 0 && y == 0) ay = 0;
			else az = (float)Math.atan(z / Math.sqrt(x * x + y * y));

            sax[0] = sax[1];
            sax[1] = sax[2];
            sax[2] = ax;
            if (sax[1] == ax) {
                sax[1] = (sax[0] + sax[2]) / 2;
            }

			say[0] = say[1];
			say[1] = say[2];
			say[2] = ay;
			if (say[1] == ay) {
				say[1] = (say[0] + say[2]) / 2;
			}

			saz[0] = saz[1];
			saz[1] = saz[2];
			saz[2] = az;
			if (saz[1] == az) {
				saz[1] = (saz[0] + saz[2]) / 2;
			}

            if (update_time.getTime() - last_updt.getTime() > 20) {
				float ax_ = sax[1];
				float ay_ = say[1];
				float az_ = saz[1];
				float flip = az_ > 0 ? 1 : -1;
				if (saz[0] > 0 && saz[1] < 0) {
					accumx += ax_ * 2;
					accumy += ay_ * 2;
				} else if (saz[0] < 0 && saz[1] > 0) {
					accumx -= ax_ * 2;
					accumy -= ay_ * 2;
				}
				ax_ *= flip;
				ay_ *= flip;

				xSeries.add(update_time, accumx + ax_);		// blue
				zSeries.add(update_time, accumy + ay_);		// yellow
				axSeries.add(update_time, az_);		// red
				axvSeries.add(update_time, 0);		// green
				renderer.setXAxisMin(update_time.getTime() - 30000);
				renderer.setXAxisMax(update_time.getTime() + 1);
				view.repaint();

				prev_ax = ax_;

//                real_axv = axv_;
//                real_ayv = ayv_;
//
//                prev_x = x;
//                prev_y = y;
//                prev_z = z;
//                prev_ax = ax;
//                prev_ay = ay;
            }
            prev_x = x;
            prev_y = y;
            prev_z = z;

        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        dataset = new XYMultipleSeriesDataset();

        renderer = new XYMultipleSeriesRenderer();
        renderer.setAxesColor(Color.BLUE);
        renderer.setAxisTitleTextSize(20);
        renderer.setChartTitle("Time");
        renderer.setChartTitleTextSize(17);
        renderer.setFitLegend(true);
        renderer.setGridColor(Color.LTGRAY);
        renderer.setYLabelsColor(0, Color.BLACK);
        renderer.setXLabelsColor(Color.BLACK);
        renderer.setMarginsColor(Color.WHITE);
        renderer.setXTitle("Time");
        renderer.setYTitle("Number");
        renderer.setMargins(new int[]{20, 30, 15, 0});
        renderer.setBarSpacing(10);
        renderer.setShowGrid(true);

        renderer.setPanEnabled(true, true);
        renderer.setZoomEnabled(true, true);
        renderer.setZoomButtonsVisible(true);

        // blue
        xSeries = new TimeSeries("x");      // Проекция оси X
        rendererSeries1 = new XYSeriesRenderer();
        rendererSeries1.setColor(Color.rgb(0, 240, 240));
        rendererSeries1.setFillPoints(true);
        rendererSeries1.setPointStyle(PointStyle.POINT);
        renderer.addSeriesRenderer(rendererSeries1);

        // yellow
        zSeries = new TimeSeries("z");      // Проекция оси Z
        rendererSeries2 = new XYSeriesRenderer();
        rendererSeries2.setColor(Color.rgb(200, 200, 10));
        rendererSeries2.setFillPoints(true);
        rendererSeries2.setPointStyle(PointStyle.POINT);
        renderer.addSeriesRenderer(rendererSeries2);

        // red
        axSeries = new TimeSeries("ax");    // Угол оси X
        rendererSeries3 = new XYSeriesRenderer();
        rendererSeries3.setColor(Color.rgb(255, 70, 70));
        rendererSeries3.setFillPoints(false);
        rendererSeries3.setPointStyle(PointStyle.POINT);
        renderer.addSeriesRenderer(rendererSeries3);

        // green
        axvSeries = new TimeSeries("axv");    // Скорость изменения угла оси X
        rendererSeries4 = new XYSeriesRenderer();
        rendererSeries4.setColor(Color.rgb(0, 150, 0));
        rendererSeries4.setFillPoints(false);
        rendererSeries4.setPointStyle(PointStyle.POINT);
        renderer.addSeriesRenderer(rendererSeries4);
    }


    @Override
    protected void onStart() {
        super.onStart();
        dataset.addSeries(0, xSeries);
        dataset.addSeries(1, zSeries);
        dataset.addSeries(2, axSeries);
        dataset.addSeries(3, axvSeries);
        view = ChartFactory.getTimeChartView(this, dataset, renderer, "Sensor");
        view.refreshDrawableState();
        view.repaint();
        setContentView(view);

        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
    }


    @Override
    protected void onPause()
    {
        super.onPause();
        timer.cancel();
        timer.purge();
        sensorManager.unregisterListener(sensorEventListener);
    }


    @Override
    protected void onResume()
    {
        super.onResume();
        final boolean AccelExists = sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
        final boolean GyroExists = sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME);
        if (GyroExists) syncSensorType = Sensor.TYPE_GYROSCOPE;
        if (AccelExists) syncSensorType = Sensor.TYPE_ACCELEROMETER;

        xSeries.clear();
		zSeries.clear();
        axSeries.clear();
        axvSeries.clear();
        timer = new Timer();
        timer.scheduleAtFixedRate(new UpdateTask(), 0, 1000/20);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        menu.add(0, 0, 0, "Pause");
        menu.add(0, 1, 1, "Resume");
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId())
        {
            case 0:
                timer.cancel();
                timer.purge();
                sensorManager.unregisterListener(sensorEventListener);
                return true;
            case 1:
                final boolean AccelExists = sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
                final boolean GyroExists = sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME);
                if (GyroExists) syncSensorType = Sensor.TYPE_GYROSCOPE;
                if (AccelExists) syncSensorType = Sensor.TYPE_ACCELEROMETER;

                xSeries.clear();
				zSeries.clear();
                axSeries.clear();
                axvSeries.clear();
                timer = new Timer();
                timer.scheduleAtFixedRate(new UpdateTask(), 0, 1000/20);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
