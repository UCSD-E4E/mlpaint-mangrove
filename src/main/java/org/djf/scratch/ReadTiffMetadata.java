package org.djf.scratch;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;



public class ReadTiffMetadata {

    public ReadTiffMetadata() {
        try {
            // the line that reads the image file
            String pathname = "/Users/davidforman/MangroveData/Orthomosaics/2018-07 Puerto San Carlos/Site 10/downsampledSite10_RGB.tiff";
            BufferedImage image = ImageIO.read(new File(pathname));
            // work with the image here ...
            //.getStandardTree()

//            ImageReader tiffReader;
//            tiffReader.setInput(,false,false); //https://docs.oracle.com/javase/8/docs/api/javax/imageio/ImageReader.html#getImageMetadata-int-java.lang.String-java.util.Set-
//            IIOMetadata jpegImageMetadata = tiffReader.getImageMetadata(0);
//            String nativeFormat = jpegImageMetadata.getNativeMetadataFormatName();
//            Node jpegImageMetadataTree = jpegImageMetadata.getAsTree(nativeFormat);

            //Use of a TIFFDirectory object may simplify gaining access to metadata values. An instance of TIFFDirectory
            // may be created from the IIOMetadata object returned by the TIFF reader using the
            // TIFFDirectory.createFromMetadata method.
//                return;
//            }
        }
        catch (IOException e) {
            // log the exception
            System.out.println(e.getMessage());
            // re-throw if desired
            System.out.println("We really got an error.");
        }
    }

    public static void main(String[] args) {
        new ReadTiffMetadata();
    }

}

//    ImageReader jpegReader;
//    ImageReader tiffReader;
//
//    // Obtain the APP1 Exif marker data from the JPEG image metadata.
//    IIOMetadata jpegImageMetadata = jpegReader.getImageMetadata(0);
//    String nativeFormat = jpegImageMetadata.getNativeMetadataFormatName();
//    Node jpegImageMetadataTree = jpegImageMetadata.getAsTree(nativeFormat);
//
//    // getExifMarkerData() returns the byte array which is the user object
//    // of the APP1 Exif marker node.
//    byte[] app1Params = getExifMarkerData(jpegImageMetadataTree);
//    if (app1Params == null) {
//            throw new IIOException("APP1 Exif marker not found.");
//            }
//
//            // Set up input, skipping Exif ID 6-byte sequence.
//            MemoryCacheImageInputStream app1ExifInput
//            = new MemoryCacheImageInputStream
//            (new ByteArrayInputStream(app1Params, 6, app1Params.length - 6));
//            tiffReader.setInput(app1ExifInput);
//
//            // Read primary IFD.
//            IIOMetadata primaryIFD = tiffReader.getImageMetadata(0);
//
//            // Read thumbnail if present.
//            BufferedImage thumbnail = null;
//            if (tiffReader.getNumImages(true) > 1) {
//            thumbnail = tiffReader.read(1, tiffReadParam);
//            }
//
//            // Read the primary image.
//            BufferedImage image = jpegReader.read(0);