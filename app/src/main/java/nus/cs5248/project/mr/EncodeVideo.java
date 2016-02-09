package nus.cs5248.project.mr;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;

import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.StatusLine;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.entity.mime.HttpMultipartMode;
import ch.boye.httpclientandroidlib.entity.mime.MultipartEntityBuilder;
import ch.boye.httpclientandroidlib.entity.mime.content.FileBody;
import ch.boye.httpclientandroidlib.impl.client.HttpClientBuilder;

public class EncodeVideo extends AsyncTask {

    private static String VIDEO_PATH_NAME = "/Pictures/test";
    private static String ENCODED_PATH_NAME = "/Pictures/";
    private String videoPath;
    private String outputPath;
    private String filename;
    private boolean set;
    private double videoTime;
    private double percentage;
    private int segmentNumber;
    private String uploadUri;
    private String processUri;
    private String filesLocation;
    private int numberOfSegmentsUploaded;
    private static final String DIR_NAME = "DASHRecorder";
    private static final String SERVER_URI = "http://pilatus.d1.comp.nus.edu.sg/~team06/";
    private static final String END_UPLOAD_PAGE = "process.php";
    private static final String UPLOAD_PHP_PAGE = "uploadpk.php";
    private int filenumber;
    int numoffiles;
    private static Context ct;
    @Override
    protected Object doInBackground(Object[] params) {

        String path = String.valueOf(params[0]);
        String destPath =  Environment.getExternalStorageDirectory().getAbsolutePath() + ENCODED_PATH_NAME;
        double splitDuration = 3.00;
        System.out.println(path + "\n" + destPath);
        numoffiles = split(path, destPath, splitDuration);

        return null;
        //return numoffiles;
        //return new Integer(numoffiles);
    }

    /**
     * Splits a video into multiple clips of specified duration of seconds
     *
     * @param path Path of the video to be segmented
     * @param destinationPath Path where the final segments have to be stored
     * @param splitDuration Duration of each clip into which we have to cut
     * @return Number of segments created in splitting of video
     */
    public int split(String path, String destinationPath, double splitDuration) {
        double startTime = 0.00;
        segmentNumber = 1;

        videoTime = 0.0;
        set = false;
        videoPath = path;
        outputPath = destinationPath;
        filename = new File(videoPath).getName().replace(".mp4", "");

        long start1 = System.currentTimeMillis();
        try {
            while (performSplit(startTime, startTime + splitDuration, segmentNumber)) {
                segmentNumber++;
                startTime += splitDuration;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        long start2 = System.currentTimeMillis();
        Log.i("DASH", "Total time taken to create " + Integer.toString(segmentNumber - 1) +
                " segments: " + Long.toString(start2 - start1) + "ms");

        return segmentNumber - 1;
    }

    /**
     * Convenience method which is called by split(double splitDuration) to perform
     * the splitting of video.
     *
     * @param startTime Start time of the new segment video
     * @param endTime End time of the new segment video
     * @param segmentNumber Segment number of the video. Used in naming for the segment
     * @return true if segment is created else false is returned
     * @throws IOException
     * @throws FileNotFoundException
     */
    private boolean performSplit(double startTime, double endTime, int segmentNumber) throws IOException, FileNotFoundException {
        int exists = 0;
        File f = new File(videoPath);

        Movie movie = MovieCreator.build(videoPath);
        Log.i("DASH", "Movie Time:" +Long.toString(movie.getTimescale()));
        List<Track> tracks = movie.getTracks();
        movie.setTracks(new LinkedList<Track>());
        // remove all tracks we will create new tracks from the old

        boolean timeCorrected = false;

        // Here we try to find a track that has sync samples. Since we can only start decoding
        // at such a sample we SHOULD make sure that the start of the new fragment is exactly
        // such a frame
        for (Track track : tracks) {
            if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
                if (timeCorrected) {
                    // This exception here could be a false positive in case we have multiple tracks
                    // with sync samples at exactly the same positions. E.g. a single movie containing
                    // multiple qualities of the same video (Microsoft Smooth Streaming file)

                    throw new RuntimeException("The startTime has already been corrected by another track with SyncSample. Not Supported.");
                }
                startTime = correctTimeToSyncSample(track, startTime, true);
                endTime = correctTimeToSyncSample(track, endTime, true);
                timeCorrected = true;
                if(!set) {
                    videoTime = correctTimeToSyncSample(track, 10000, true);
                    set = true;
                    Log.i("DASH", "Video total time =" + videoTime);
                }
            }
        }

        percentage = (startTime * 100) / videoTime;
        publishProgress(percentage);

        if (startTime == endTime)
            return false;

        for (Track track : tracks) {
            long currentSample = 0;
            double currentTime = 0;
            double lastTime = 0;
            long startSample1 = 0;
            long endSample1 = -1;

            for (int i = 0; i < track.getSampleDurations().length; i++) {
                long delta = track.getSampleDurations()[i];


                if (currentTime > lastTime && currentTime <= startTime) {
                    // current sample is still before the new starttime
                    startSample1 = currentSample;
                }
                if (currentTime > lastTime && currentTime <= endTime) {
                    // current sample is after the new start time and still before the new endtime
                    endSample1 = currentSample;
                }

                lastTime = currentTime;
                currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
                currentSample++;
            }
            Log.i("DASH", "Start time = " + startTime + ", End time = " + endTime);
            movie.addTrack(new CroppedTrack(track, startSample1, endSample1));
        }
        long start1 = System.currentTimeMillis();
        Container out = new DefaultMp4Builder().build(movie);
        long start2 = System.currentTimeMillis();

        int n = Integer.parseInt(filename.substring(4));
        int number = 2*(n-1) + segmentNumber;
        //if(n>=2 && number>=3) number++;
        FileOutputStream fos = new FileOutputStream(outputPath + String.format("Seg%d.mp4", number));
        FileChannel fc = fos.getChannel();
        out.writeContainer(fc);
        fc.close();
        fos.close();

        filesLocation = new String(outputPath + String.format("Seg%d.mp4", number));

        uploadUri = SERVER_URI + UPLOAD_PHP_PAGE;
        processUri = SERVER_URI + END_UPLOAD_PAGE;
        numberOfSegmentsUploaded = 0;
        uploadFilesFromLocation();

        long start3 = System.currentTimeMillis();
        Log.i("DASH", "Building IsoFile took : " + (start2 - start1) + "ms");
        Log.i("DASH", "Writing IsoFile took  : " + (start3 - start2) + "ms");
        return true;
    }

    /**
     * Convenience method which gives the nearest next or previous time
     * where the segmentation of the video can be performed.
     *
     * @param track Track which needs to be scanned to find the next sync sample
     * @param cutHere Time at which the segmentation needs to be done
     * @param next boolean false if want the cutTime less than cutHere else true
     * @return nearest cutTime where the segmentation could be done
     */
    private double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getSampleDurations().length; i++) {
            long delta = track.getSampleDurations()[i];

            if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
                // samples always start with 1 but we start with zero therefore +1
                timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
            }
            currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
            currentSample++;

        }
        double previous = 0;
        for (double timeOfSyncSample : timeOfSyncSamples) {
            if (timeOfSyncSample >= cutHere) {
                if (next) {
                    return timeOfSyncSample;
                } else {
                    return previous;
                }
            }
            previous = timeOfSyncSample;
        }
        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }
    protected void onPostExecute(String file_url) {


    }
    //return numoffiles;}

    public void uploadFilesFromLocation()
    {
        //File[] files = new File(filesLocation).listFiles();
        //numberOfSegments = files.length;
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost postRequest = new HttpPost(uploadUri);
        HttpPost endRequest = new HttpPost(processUri);

        //String folderName = files[0].getName().substring(0, files[0].getName().indexOf("---1.mp4"));

        Log.i("DASH", "Entering the for loop for video uploads");
		/*for (int i=numberOfSegmentsUploaded;i<=files.length;i++)
		{
			MultipartEntityBuilder reqEntity = MultipartEntityBuilder.create();
			reqEntity.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
			if (i==files.length)
			{
				reqEntity.addTextBody("foldername", folderName);
				HttpEntity entity = reqEntity.build();
				endRequest.setEntity(entity);
			}
			else
			{
				String videoPath = files[i].getAbsolutePath();
	            FileBody filebodyVideo = new FileBody(new File(videoPath));
	            reqEntity.addPart("uploaded", filebodyVideo);
				HttpEntity entity = reqEntity.build();
				postRequest.setEntity(entity);
			}
			*/

        MultipartEntityBuilder reqEntity = MultipartEntityBuilder.create();
        reqEntity.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        String videoPath = filesLocation;
        FileBody filebodyVideo = new FileBody(new File(videoPath));
        Log.i("sunny",""+filebodyVideo.getFilename().substring(0,filebodyVideo.getFilename().indexOf('.')));
        reqEntity.addPart("uploaded", filebodyVideo);
        HttpEntity entity = reqEntity.build();
        postRequest.setEntity(entity);
        try
        {
            HttpResponse response;
            Log.i("DASH", "Executing the httpClient execute");
            response = httpClient.execute(postRequest);
				/*if (i==files.length)
				{
					response = httpClient.execute(endRequest);
				}
				else
				{
					response = httpClient.execute(postRequest);
				}*/
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
            String sResponse;
            StringBuilder s = new StringBuilder();

            StatusLine a = response.getStatusLine();
            Log.i("DASH" , a.toString());

            while ((sResponse = reader.readLine()) != null)
            {
                s = s.append(sResponse);
            }
            Log.i("DASH", s.toString());
        }
        catch(Exception e)
        {
            // If the upload has failed, the thread sleeps for a while and tries again
            Log.i("DASH" , "Exception raised while trying to upload the video"+ e.getMessage());
            //publishProgress(-1);
            //i--;
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            //  continue;
        }
        MultipartEntityBuilder reqEntity1 = MultipartEntityBuilder.create();
        reqEntity1.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        reqEntity1.addTextBody("foldername",filebodyVideo.getFilename().substring(0,filebodyVideo.getFilename().indexOf('.')));
        HttpEntity entity1 = reqEntity1.build();
        endRequest.setEntity(entity1);


			/*try
			{
				HttpResponse response;
				Log.i("DASH1", "Executing the httpClient execute");
				response = httpClient.execute(endRequest);
				if (i==files.length)
				{
					response = httpClient.execute(endRequest);
				}
				else
				{
					response = httpClient.execute(postRequest);
				}
				BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
				String sResponse;
		        StringBuilder s = new StringBuilder();

		        StatusLine a = response.getStatusLine();
				Log.i("DASH1" , a.toString());

		        while ((sResponse = reader.readLine()) != null)
		        {
		        	s = s.append(sResponse);
		        }
		        Log.i("DASH1", s.toString());
			}
			catch(Exception e)
			{
				// If the upload has failed, the thread sleeps for a while and tries again
				Log.i("DASH1" , "Exception raised while trying to upload the video"+ e.getMessage());
				//publishProgress(-1);
				//i--;
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
		      //  continue;
			}*/
        //numberOfSegmentsUploaded++;
        //if (numberOfSegmentsUploaded<=numberOfSegments)
        //	publishProgress((numberOfSegmentsUploaded * 100) / numberOfSegments);
    }
    //}

}
