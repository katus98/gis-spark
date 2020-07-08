package edu.zju.gis.hls.trajectory.datastore.storage.writer;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * @author Hu
 * @date 2019/12/19
 **/
@Getter
@Setter
@ToString
public abstract class LayerWriterConfig implements Serializable {
  protected String sinkPath;
}
