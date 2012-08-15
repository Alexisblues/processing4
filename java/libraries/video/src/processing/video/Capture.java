/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2004-12 Ben Fry and Casey Reas
  The previous version of this code was developed by Hernando Barragan

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 2.1 of the License, or (at your option) any later version.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.video;

import processing.core.*;

import java.nio.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.io.File;
import java.lang.reflect.*;

import org.gstreamer.*;
import org.gstreamer.Buffer;
import org.gstreamer.elements.*;
import org.gstreamer.interfaces.PropertyProbe;
import org.gstreamer.interfaces.Property;

/**
   * ( begin auto-generated from Capture.xml )
   * 
   * Datatype for storing and manipulating video frames from an attached 
   * capture device such as a camera. Use <b>Capture.list()</b> to show the 
   * names of any attached devices. Using the version of the constructor 
   * without <b>name</b> will attempt to use the last device used by a 
   * QuickTime program.
   * 
   * ( end auto-generated )
 * 
 * <h3>Advanced</h3>
 * Class for storing and manipulating video frames from an attached capture
 * device such as a camera.
 * @webref video
 * @usage application
 */
public class Capture extends PImage implements PConstants {
  public static String capturePlugin;
  public static String devicePropertyName;
  public static String indexPropertyName;
  // Default gstreamer capture plugin for each platform, and property names.
  static {
    if (PApplet.platform == MACOSX) {
      capturePlugin = "qtkitvideosrc";
      devicePropertyName = "device-name"; 
      indexPropertyName = "device-index";
    } else if (PApplet.platform == WINDOWS) {
      capturePlugin = "ksvideosrc";
      devicePropertyName = "device-name";
      indexPropertyName = "device-index";
    } else if (PApplet.platform == LINUX) {
      capturePlugin = "v4l2src";
      // The "device" property in v4l2src expects the device location (/dev/video0, etc). 
      // v4l2src has "device-name", which requires the human-readable name... but how 
      // to query in linux?.
      devicePropertyName = "device";
      indexPropertyName = "device-fd";
    } else {}
  }  
  
  protected String source;
  
  protected boolean capturing = false;
  
  protected String fps;  
  protected int bufWidth;
  protected int bufHeight;
  
  protected Pipeline pipeline;
  protected Element gsource;
  
  protected Method captureEventMethod;
  protected Object eventHandler;
  
  protected boolean available;
  protected boolean pipelineReady;
  protected boolean newFrame;

  protected RGBDataAppSink rgbSink = null;
  protected int[] copyPixels = null;
  
  protected boolean firstFrame = true;
  
  //protected ArrayList<Resolution> resolutions;  
  protected int reqWidth;
  protected int reqHeight;  
  
  protected boolean useBufferSink = false; 
  protected Object bufferSink;
  protected Method sinkCopyMethod;
  protected Method sinkSetMethod;
  protected String copyMask;    
  protected Buffer natBuffer = null;
  protected BufferDataAppSink natSink = null;  
  
  
  public Capture(PApplet parent, String requestConfig) {
    
  }

  
  /**
   * @param parent typically use "this"
   * @param requestWidth width of the frame
   * @param requestHeight height of the frame
   */
  public Capture(PApplet parent, int requestWidth, int requestHeight) {
    super(requestWidth, requestHeight, RGB);
    initGStreamer(parent, requestWidth, requestHeight, capturePlugin, null, "");
  }

  
  /**
   * <h3>Advanced</h3>
   * Constructor that takes resolution and framerate.
   * 
   * @param frameRate number of frames to read per second
   */  
  public Capture(PApplet parent, int requestWidth, int requestHeight, int frameRate) {
    super(requestWidth, requestHeight, RGB);
    initGStreamer(parent, requestWidth, requestHeight, capturePlugin, null, frameRate + "/1");
  }

  
  /**
   * <h3>Advanced</h3>
   * This constructor allows to specify resolution and camera name.
   * 
   * @param cameraName name of the camera
   */   
  public Capture(PApplet parent, int requestWidth, int requestHeight, String cameraName) {
    super(requestWidth, requestHeight, RGB);
    HashMap<String, Object> properties = new HashMap<String, Object>();
    if (devicePropertyName.equals("")) {
      // For plugins without device name property, the name is casted as an index
      properties.put(indexPropertyName, PApplet.parseInt(cameraName));
    } else {
      properties.put(devicePropertyName, cameraName);
    }
    initGStreamer(parent, requestWidth, requestHeight, capturePlugin, properties, "");
  }

  
  /**
   * <h3>Advanced</h3>
   * This constructor allows to specify the camera name and the desired framerate, in addition to the resolution.
   */     
  public Capture(PApplet parent, int requestWidth, int requestHeight, String cameraName, int frameRate) {
    super(requestWidth, requestHeight, RGB);
    HashMap<String, Object> properties = new HashMap<String, Object>();
    if (devicePropertyName.equals("")) {
      // For plugins without device name property, the name is casted as an index
      properties.put(indexPropertyName, PApplet.parseInt(cameraName));
    } else {
      properties.put(devicePropertyName, cameraName);
    }
    initGStreamer(parent, requestWidth, requestHeight, capturePlugin, properties, frameRate + "/1");
  }  
  
  
  /**
   * <h3>Advanced</h3>
   * This constructor allows to specify the source element, properties and desired framerate (in fraction form).
   */       
//  public Capture(PApplet parent, int requestWidth, int requestHeight, String sourceName, 
//                 HashMap<String, Object> properties, String frameRate) {
//    super(requestWidth, requestHeight, RGB);
//    initGStreamer(parent, requestWidth, requestHeight, sourceName, properties, frameRate);    
//  }
  

  /**
   * Disposes all the native resources associated to this capture device.
   */    
  public void dispose() {
    if (pipeline != null) {
      try {
        if (pipeline.isPlaying()) {
          pipeline.stop();
          pipeline.getState();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
            
      pixels = null;      
      
      copyPixels = null;
      if (rgbSink != null) {
        rgbSink.removeListener();
        rgbSink.dispose();
        rgbSink = null;
      }      
      
      natBuffer = null;
      if (natSink != null) {
        natSink.removeListener();
        natSink.dispose();
        natSink = null;
      }        
      
      pipeline.dispose();
      pipeline = null;
    }
  }    
  
  
  /**
   * Finalizer of the class.
   */  
  protected void finalize() throws Throwable {
    try {
      dispose();
    } finally {
      super.finalize();
    }
  }       
 
  
  /**
   * ( begin auto-generated from Capture_available.xml )
   * 
   * Returns "true" when a new video frame is available to read.
   * 
   * ( end auto-generated )
   * 
   * @webref capture
   * @usage web_application
   */
  public boolean available() {
    return available;
  }
  
  
  /**
   * Starts capturing frames from the selected device.
   */
  public void start() {
    //boolean init = false;
    if (!pipelineReady) {
      initPipeline();
      //init = true;
    }
    
    capturing = true;
    pipeline.play();
    pipeline.getState();
    
//    if (init) {
//      // Resolution and FPS initialization needs to be done after the
//      // pipeline is set to play.
//      getResolutions();
//      checkResolutions();
//    }
  }

  
  /**
   * ( begin auto-generated from Capture_stop.xml )
   * 
   * Stops capturing frames from an attached device.
   * 
   * ( end auto-generated )
   * 
   * @webref capture
   * @usage web_application
   */
  public void stop() {
    if (!pipelineReady) {
      initPipeline();
    }
    
    capturing = false;
    pipeline.stop();
    pipeline.getState();
  }  
  
  
  /** 
   * ( begin auto-generated from Capture_read.xml )
   * 
   * Reads the current video frame.
   * 
   * ( end auto-generated )
   * 
   * <h3>Advanced</h3>
   * This method() and invokeEvent() are now synchronized, so that invokeEvent()
   * can't be called whilst we're busy reading. Problematic frame error
   * fixed by Charl P. Botha <charlbotha.com>
   * 
   * @webref capture
   * @usage web_application
   */
  public synchronized void read() {
    if (useBufferSink) { // The native buffer from gstreamer is copied to the buffer sink.
      if (natBuffer == null) {         
        return;
      }
    
      if (firstFrame) {
        super.init(bufWidth, bufHeight, ARGB);
        firstFrame = false;
      }      
      
      if (bufferSink == null) {
        Object cache = getCache(parent.g);
        if (cache == null) {
          return;
        }        
        setBufferSink(cache);        
        getSinkMethods();
      }
      
      ByteBuffer byteBuffer = natBuffer.getByteBuffer();
      
      try {
        sinkCopyMethod.invoke(bufferSink, new Object[] { natBuffer, byteBuffer, bufWidth, bufHeight });
      } catch (Exception e) {
        e.printStackTrace();
      }
      
      natBuffer = null;       
    } else { // The pixels just read from gstreamer are copied to the pixels array. 
      if (copyPixels == null) {
        return;
      }    
      
      if (firstFrame) {
        super.init(bufWidth, bufHeight, RGB);
        firstFrame = false;
      }
      
      int[] temp = pixels;
      pixels = copyPixels;
      updatePixels();
      copyPixels = temp;         
    }
    
    available = false;
    newFrame = true;
  }
  
  
   
  ////////////////////////////////////////////////////////////
  
  // List methods.
  
  
  /**
   * ( begin auto-generated from Capture_list.xml )
   * 
   * Gets a list of all available capture devices such as a camera. Use 
   * <b>print()</b> to write the information to the text window.
   * 
   * ( end auto-generated )
   * 
   * @webref capture
   * @usage web_application
   */  
  static public String[] list() {    
    return list(capturePlugin);
  }

  
  /**
   * <h3>Advanced</h3>
   * Get a list of all available captures as a String array. i.e.
   * println(Capture.list()) will show you the goodies.
   * 
   * @param sourceName String
   */
  static public String[] list(String sourceName) {
    if (devicePropertyName.equals("")) {
      return list(sourceName, indexPropertyName);  
    } else {
      return list(sourceName, devicePropertyName);
    }
  }
  
  
  static protected String[] list(String sourceName, String propertyName) {
    Video.init();
    ArrayList<String> devices = listDevices(sourceName, propertyName);
    
    ArrayList<String> configList = new ArrayList<String>();
    for (String device: devices) {
      ArrayList<String> resolutions = listResolutions(sourceName, propertyName, device);      
      for (String res: resolutions) {
        configList.add("name=" + device + "," + res);
      }
    }
    
    String[] configs = new String[configList.size()];
    for (int i = 0; i < configs.length; i++) {
      configs[i] = configList.get(i);    
    }
    
    return configs;
  }

  
  static protected ArrayList<String> listDevices(String sourceName, String propertyName) {
    ArrayList<String> devices = new ArrayList<String>();
    try {
      // Using property-probe interface
      Element videoSource = ElementFactory.make(sourceName, "Source");
      PropertyProbe probe = PropertyProbe.wrap(videoSource);
      if (probe != null) {
        Property property = probe.getProperty(propertyName);
        if (property != null) {
          Object[] values = probe.getValues(property);
          if (values != null) {
            for (int i = 0; i < values.length; i++)
              if (values[i] instanceof String) {
                devices.add((String)values[i]);
              }
          }
        }
      }
    } catch (IllegalArgumentException e) {
      if (PApplet.platform == LINUX) {
        // Linux hack to detect currently connected cameras
        // by looking for device files named /dev/video0, 
        // /dev/video1, etc.
        devices = new ArrayList<String>();
        String dir = "/dev";
        File libPath = new File(dir);
        String[] files = libPath.list();
        if (files != null) {
          for (int i = 0; i < files.length; i++) {
            if (-1 < files[i].indexOf("video")) {
              devices.add("/dev/" + files[i]);
            }
          }
        }
      } else {      
        PGraphics.showWarning("The capture plugin doesn't support device query!");
        devices = new ArrayList<String>();
      }      
    }
    return devices;
  }
  
  
  static protected ArrayList<String> listResolutions(String sourceName, String propertyName, Object propertyValue) {
    // Creating temporary pipeline so that we can query 
    // the resolutions supported by the device.
    Pipeline testPipeline = new Pipeline("test");    
    Element source = ElementFactory.make(sourceName, "source");    
    source.set(propertyName, propertyValue);
        
    BufferDataAppSink sink = new BufferDataAppSink("sink", "",
        new BufferDataAppSink.Listener() {
          public void bufferFrame(int w, int h, Buffer buffer) { }
        });
    testPipeline.addMany(source, sink);
    Element.linkMany(source, sink);      
    
    testPipeline.play();
    testPipeline.getState();    
        
    ArrayList<String> resolutions = new ArrayList<String>(); 
    for (Pad pad : source.getPads()) {
      
      Caps caps = pad.getCaps();
      int n = caps.size(); 
      for (int i = 0; i < n; i++) {           
        Structure str = caps.getStructure(i);
        
        if (!str.hasIntField("width") || !str.hasIntField("height")) continue;
        
        int w = ((Integer)str.getValue("width")).intValue();
        int h = ((Integer)str.getValue("height")).intValue();          
        
        if (PApplet.platform == WINDOWS) {
          // In Windows the getValueList() method doesn't seem to
          // return a valid list of fraction values, so working on
          // the string representation of the caps structure.            
          addResFromString(resolutions, str.toString(), w, h);
        } else {
          addResFromStructure(resolutions, str, w, h);
        }          
      }
    }
    
    testPipeline.stop();
    testPipeline.getState();
    
    if (sink != null) {
      sink.removeListener();
      sink.dispose();
    }        
    
    testPipeline.dispose();
    
    return resolutions;
  }   
  
  
  static protected void addResFromString(ArrayList<String> res, String str, int w, int h) {    
    int n0 = str.indexOf("framerate=(fraction)");
    if (-1 < n0) {
      String temp = str.substring(n0 + 20, str.length());
      int n1 = temp.indexOf("[");
      int n2 = temp.indexOf("]");
      if (-1 < n1 && -1 < n2) {
        // A list of fractions enclosed between '[' and ']'
        temp = temp.substring(n1 + 1, n2);  
        String[] fractions = temp.split(",");
        for (int k = 0; k < fractions.length; k++) {
          String fpsStr = fractions[k].trim(); 
          res.add(makeResolutionString(w, h, fpsStr));
        }
      } else {
        // A single fraction
        int n3 = temp.indexOf(",");
        int n4 = temp.indexOf(";");
        if (-1 < n3 || -1 < n4) {
          int n5 = -1;
          if (n3 == -1) {
            n5 = n4;
          } else if (n4 == -1) {
            n5 = n3;
          } else {
            n5 = PApplet.min(n3, n4);
          }
          
          temp = temp.substring(0, n5);
          String fpsStr = temp.trim();                  
          res.add(makeResolutionString(w, h, fpsStr));
        }
      }    
    }
  }
  
  
  static protected void addResFromStructure(ArrayList<String> res, Structure str, int w, int h) {
    boolean singleFrac = false;
    try {
      Fraction fr = str.getFraction("framerate");
      res.add(makeResolutionString(w, h, fr.numerator, fr.denominator));
      singleFrac = true;
    } catch (Exception e) { 
    }
    
    if (!singleFrac) {
      ValueList flist = null;
      
      try {
        flist = str.getValueList("framerate");
      } catch (Exception e) { 
      }
      
      if (flist != null) {
        // All the framerates are put together, but this is not
        // entirely accurate since there might be some of them'
        // that work only for certain resolutions.
        for (int k = 0; k < flist.getSize(); k++) {
          Fraction fr = flist.getFraction(k);
          res.add(makeResolutionString(w, h, fr.numerator, fr.denominator));
        }              
      }            
    }    
  }

  
  static protected String makeResolutionString(int width, int height, int fpsDenominator, int fpsNumerator) {
    String res = "size=" + width + "x" + height + ",fps=" + fpsDenominator;
    if (fpsNumerator != 1) {
      res += "/" + fpsNumerator;  
    } 
    return res;
  }

  
  static protected String makeResolutionString(int width, int height, String fpsStr) {
    String res = "size=" + width + "x" + height;    
    String[] parts = fpsStr.split("/");
    if (parts.length == 2) {      
      int fpsDenominator = PApplet.parseInt(parts[0]);
      int fpsNumerator = PApplet.parseInt(parts[1]);
      res += ",fps=" + fpsDenominator;
      if (fpsNumerator != 1) {
        res += "/" + fpsNumerator;  
      }
    }    
    return res;
  }

  
  ////////////////////////////////////////////////////////////
  
  // Initialization methods.
  
    
  // The main initialization here.
  protected void initGStreamer(PApplet parent, int requestWidth, int requestHeight, String sourceName,
                               HashMap<String, Object> properties, String frameRate) {
    this.parent = parent;

//    String[] cameras = list(sourceName);
//    if (cameras.length == 0) {
//      throw new RuntimeException("There are no cameras available for capture.");
//    } 
    
    Video.init();

    // register methods
    parent.registerDispose(this);

    setEventHandlerObject(parent);

    pipeline = new Pipeline("Video Capture");
    
    this.source = sourceName;
    
    fps = frameRate;
    reqWidth = requestWidth;
    reqHeight = requestHeight;    
    
    gsource = ElementFactory.make(sourceName, "Source");
    
    if (properties != null) {
      Iterator<String> it = properties.keySet().iterator();    
      while (it.hasNext()) {
        String name = it.next();
        Object value = properties.get(name);
        gsource.set(name, value);
      }    
    }
    
    bufWidth = bufHeight = 0;
    pipelineReady = false;    
  }

  
  protected void initPipeline() {
    String fpsStr = "";
    if (!fps.equals("")) {
      // If the framerate string is empty we left the source element
      // to use the default value.      
      fpsStr = ", framerate=" + fps;
    }    
    
    if (bufferSink != null || (Video.useGLBufferSink && parent.g.isGL())) {
      useBufferSink = true;
      
      if (bufferSink != null) {
        getSinkMethods();        
      }  
      
      if (copyMask == null || copyMask.equals("")) { 
        initCopyMask();
      }
      
      //String caps = "width=" + reqWidth + ", height=" + reqHeight + fpsStr + ", " + copyMask;
      String caps = copyMask; // default, use to query resolutions
      
      natSink = new BufferDataAppSink("nat", caps,
          new BufferDataAppSink.Listener() {
            public void bufferFrame(int w, int h, Buffer buffer) {
              invokeEvent(w, h, buffer);
            }
          });
    
      natSink.setAutoDisposeBuffer(false);
      
      // No need for rgbSink.dispose(), because the addMany() doesn't increment the
      // refcount of the videoSink object.      
      
      pipeline.addMany(gsource, natSink);
      Element.linkMany(gsource, natSink);      
      
    } else {
      Element conv = ElementFactory.make("ffmpegcolorspace", "ColorConverter");

      Element videofilter = ElementFactory.make("capsfilter", "ColorFilter");
      videofilter.setCaps(new Caps("video/x-raw-rgb, width=" + reqWidth + ", height=" + reqHeight + 
                                   ", bpp=32, depth=24" + fpsStr));
      
      rgbSink = new RGBDataAppSink("rgb", 
          new RGBDataAppSink.Listener() {
            public void rgbFrame(int w, int h, IntBuffer buffer) {
              invokeEvent(w, h, buffer);
            }
          });
      
      // Setting direct buffer passing in the video sink.
      rgbSink.setPassDirectBuffer(Video.passDirectBuffer);      
      
      // No need for rgbSink.dispose(), because the addMany() doesn't increment the
      // refcount of the videoSink object.      
      
      pipeline.addMany(gsource, conv, videofilter, rgbSink);
      Element.linkMany(gsource, conv, videofilter, rgbSink);    
    } 
    
    pipelineReady = true;
    newFrame = false;
  }
  
  
  /**
   * Uses a generic object as handler of the capture. This object should have a
   * movieEvent method that receives a GSMovie argument. This method will
   * be called upon a new frame read event. 
   * 
   */
  protected void setEventHandlerObject(Object obj) {
    eventHandler = obj;

    try {
      captureEventMethod = parent.getClass().getMethod("captureEvent",
          new Class[] { Capture.class });
    } catch (Exception e) {
      // no such method, or an error.. which is fine, just ignore
    }
  }
    
  
  ////////////////////////////////////////////////////////////
  
  // Stream event handling.   
  
  
  /**
   * invokeEvent() and read() are synchronized so that they can not be
   * called simultaneously. when they were not synchronized, this caused
   * the infamous problematic frame crash.
   * found and fixed by Charl P. Botha <charlbotha.com>
   */
  protected synchronized void invokeEvent(int w, int h, IntBuffer buffer) {
    available = true;
    bufWidth = w;
    bufHeight = h;
    if (copyPixels == null) {
      copyPixels = new int[w * h];
    }
    buffer.rewind();    
    try {
      buffer.get(copyPixels);
    } catch (BufferUnderflowException e) {
      e.printStackTrace();
      copyPixels = null;
      return;
    }
    
    // Creates a movieEvent.
    if (captureEventMethod != null) {
      try {
        captureEventMethod.invoke(eventHandler, new Object[] { this });
      } catch (Exception e) {
        System.err.println("error, disabling captureEvent() for capture object");
        e.printStackTrace();
        captureEventMethod = null;
      }
    }
  }

  protected synchronized void invokeEvent(int w, int h, Buffer buffer) {
    available = true;
    bufWidth = w;
    bufHeight = h;        
    natBuffer = buffer;
    
    // Creates a movieEvent.
    if (captureEventMethod != null) {
      try {
        captureEventMethod.invoke(eventHandler, new Object[] { this });
      } catch (Exception e) {
        System.err.println("error, disabling captureEvent() for capture object");
        e.printStackTrace();
        captureEventMethod = null;
      }
    }
  }  

  
  
  
  
//  protected void checkResolutions() {
//    boolean suppRes = false;
//    for (int i = 0; i < resolutions.size(); i++) {
//      Resolution res = resolutions.get(i);
//      if (reqWidth == res.width && reqHeight == res.height && (fps.equals("") || fps.equals(res.fpsString))) {
//        suppRes = true;
//        break;
//      }
//    }
//    
//    if (!suppRes) {
//      String fpsStr = "";
//      if (!fps.equals("")) {
//        fpsStr = ", " + fps + "fps";
//      }      
//      String helpStr = "Use one of the following resolutions instead:\n";
//      for (int i = 0; i < resolutions.size(); i++) {
//        Resolution res = resolutions.get(i);
//        helpStr += res.toString() + '\n';
//      }      
//      throw new RuntimeException("The requested resolution of " + reqWidth + "x" + reqHeight + fpsStr + 
//                                 " is not supported by the selected capture device.\n" + helpStr);
//    }     
//  }
  
  
  /*
  protected void getResolutions() {
    resolutions = new ArrayList<Resolution>(); 
   
    for (Element src : pipeline.getSources()) {
      for (Pad pad : src.getPads()) {
        
        Caps caps = pad.getCaps();
        int n = caps.size(); 
        for (int i = 0; i < n; i++) {           
          Structure str = caps.getStructure(i);
          
          if (!str.hasIntField("width") || !str.hasIntField("height")) continue;
          
          int w = ((Integer)str.getValue("width")).intValue();
          int h = ((Integer)str.getValue("height")).intValue();          
          
          if (PApplet.platform == WINDOWS) {
            // In Windows the getValueList() method doesn't seem to
            // return a valid list of fraction values, so working on
            // the string representation of the caps structure.            
            getFpsFromString(str.toString(), w, h);

          } else {
            getFpsFromStructure(str, w, h);
          }          
        }
      }
    }
  }  
  */
  
  
  ////////////////////////////////////////////////////////////
  
  // Buffer source interface. 

  
  /**
   * Sets the object to use as destination for the frames read from the stream.
   * The color conversion mask is automatically set to the one required to
   * copy the frames to OpenGL.
   * 
   * @param Object dest
   */  
  public void setBufferSink(Object sink) {
    bufferSink = sink;
    initCopyMask();
  }  
  
  
  /**
   * Sets the object to use as destination for the frames read from the stream.
   * 
   * @param Object dest
   * @param String mask 
   */    
  public void setBufferSink(Object sink, String mask) {
    bufferSink = sink;
    copyMask = mask;
  }  
  
  
  public boolean hasBufferSink() {
    return bufferSink != null;
  }
  
  
  public synchronized void disposeBuffer(Object buf) {
    ((Buffer)buf).dispose();
  }

  
  protected void getSinkMethods() {
    try {      
      sinkCopyMethod = bufferSink.getClass().getMethod("copyBufferFromSource",
          new Class[] { Object.class, ByteBuffer.class, int.class, int.class });         
    } catch (Exception e) {
      throw new RuntimeException("Movie: provided sink object doesn't have a copyBufferFromSource method.");
    }
    
    try {            
      sinkSetMethod = bufferSink.getClass().getMethod("setBufferSource", new Class[] { Object.class });
      sinkSetMethod.invoke(bufferSink, new Object[] { this });            
    } catch (Exception e) {
      throw new RuntimeException("Movie: provided sink object doesn't have a setBufferSource method.");
    }    
  }
  
  
  protected void initCopyMask() {
    if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
      copyMask = "red_mask=(int)0xFF000000, green_mask=(int)0xFF0000, blue_mask=(int)0xFF00";        
    } else {
      copyMask = "red_mask=(int)0xFF, green_mask=(int)0xFF00, blue_mask=(int)0xFF0000";
    }       
  }
}
