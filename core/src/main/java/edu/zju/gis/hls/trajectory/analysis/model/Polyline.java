package edu.zju.gis.hls.trajectory.analysis.model;

import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.LineString;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Hu
 * @date 2019/9/19
 **/
@Getter
@Setter
public class Polyline extends Feature<LineString> {

  public Polyline(String fid, LineString geometry, LinkedHashMap<Field, Object> attributes) {
    super(fid, geometry, attributes);
  }

  public Polyline(Polyline f) {
    super(f);
  }

}
