package com.freshi.presignphoto.function;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.HttpMethod;
import com.amazonaws.SdkClientException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * a Freshi microservice... a Lambda function that provide presign URL's for
 * photo's that need to be uploaded to the content store. URL expires on same
 * day, and allow client to upload images (jpg & png) directly from HTML.
 */
public class presignURL implements RequestHandler<Map, Map> {
	// Variables...
	private static final float MAX_WIDTH = 100;
	private static final float MAX_HEIGHT = 100;
	private final String JPG_TYPE = "jpg";
	private final String JPG_MIME = "image/jpeg";
	private final String PNG_TYPE = "png";
	private final String PNG_MIME = "image/png";
	private final String INVALID_TYPE = "invalid";
	// for use to ensure input/output is readable for logging purpose
	Gson gson = new GsonBuilder().setPrettyPrinting().create();
	
	private final String bucketName = "freshi-photomaster";

	@Override
	/**
	 * main lambda function
	 */
	public Map handleRequest(Map input, Context context) {
		// initiate response object. Structure:<String, String> "filename={name}":"URL"
		Map<String, String> response = new HashMap<String, String>();
		
		if (input != null) {
			// TODO can remove the following...
			//context.getLogger().log("EVENT: " + gson.toJson(input) + "\n\n");
			//context.getLogger().log("TEST TYPE....\n");
			// iterate through filename key to determine image mime type valid
			//context.getLogger().log("FILE COUNT: " + input.size() + "\n");
			input.forEach((k, v) -> {
				// TODO logging - can remove
				//context.getLogger()
				//.log("  FILE: " + k.toString() + " - VALID: " + referImageType(k.toString(), context) + "\n");
				String urlresponse=null;
				try {
					urlresponse = generateURL(k.toString());
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// TODO missing test to ensure urlreponse is not null....
				String value = (referImageType(k.toString(), context) == true ? urlresponse : INVALID_TYPE);
				String returnValue = (String) response.put((String) k, value);
				// test response from Map.put()...null==good
				//context.getLogger().log("MAP RESPONSE for " + (String) k + " :" + returnValue == null ? "good"
				//		: "..something went wrong" + "\n");
			});
		}
		// can remove the following...
		context.getLogger().log("RESPONSE: " + gson.toJson(response) + "\n\n");

		return response;
	}

	/**
	 * generate a S3 URL
	 * 
	 * @throws MalformedURLException
	 */
	private String generateURL(String filename) throws MalformedURLException {
		// Initiate variables for method
		URL url = null;
		
		try {
			// Create local client with region same as function - else needs to override
			AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();
			
			// Set the pre-signed URL to expire after one hour.
			java.util.Date expiration = new java.util.Date();
			long expTimeMillis = expiration.getTime();
			expTimeMillis += 1000 * 60 * 60;
			expiration.setTime(expTimeMillis);

			// Generate the pre-signed URL.
			GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName,
					filename).withMethod(HttpMethod.PUT).withExpiration(expiration);
			url = s3Client.generatePresignedUrl(generatePresignedUrlRequest);
			
		} catch (AmazonServiceException e) {
			// The call was transmitted successfully, but Amazon S3 couldn't process
			// it, so it returned an error response.
			e.printStackTrace();
		} catch (SdkClientException e) {
			// Amazon S3 couldn't be contacted for a response, or the client
			// couldn't parse the response from Amazon S3.
			e.printStackTrace();
		}
		
		return url!=null? url.toString():"INVALID";
		
	}

	/**
	 * determine image type and if valid or not
	 */
	private boolean referImageType(String filename, Context context) {
		// Object key may have spaces or unicode non-ASCII characters.

		// Infer the image type - invalid or not jpg or png then return false
		Matcher matcher = Pattern.compile(".*\\.([^\\.]*)").matcher(filename);
		if (!matcher.matches()) {
			context.getLogger().log("Unable to infer image type for key " + filename);
			return false;
		}
		String imageType = matcher.group(1);

		if (!(JPG_TYPE.equals(imageType)) && !(PNG_TYPE.equals(imageType))) {
			context.getLogger().log("Skipping non-image " + filename);
			return false;
		}
		// else return true
		return true;

	}
}
