package edu.zju.gis.hls.gisspark.model.stats;

import edu.zju.gis.hls.gisspark.model.BaseModel;
import edu.zju.gis.hls.gisspark.model.util.SparkSessionType;
import edu.zju.gis.hls.trajectory.analysis.model.Feature;
import edu.zju.gis.hls.trajectory.analysis.model.Field;
import edu.zju.gis.hls.trajectory.analysis.rddLayer.Layer;
import edu.zju.gis.hls.trajectory.datastore.storage.LayerFactory;
import edu.zju.gis.hls.trajectory.datastore.storage.reader.LayerReader;
import edu.zju.gis.hls.trajectory.datastore.storage.reader.LayerReaderConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.Optional;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.storage.StorageLevel;
import scala.Tuple2;

/**
 * @author Hu
 * @date 2020/9/15
 * 二调数据关联生成merge表
 **/
@Slf4j
public class LandFlowPreProcess extends BaseModel<LandFlowPreProcessArgs> {

    public LandFlowPreProcess(SparkSessionType type, String[] args) {
        super(type, args);
    }

    @Override
    protected void run() throws Exception {

        LayerReaderConfig lxdwConfig = LayerFactory.getReaderConfig(this.arg.getLxdwReaderConfig());
        LayerReaderConfig xzdwConfig = LayerFactory.getReaderConfig(this.arg.getXzdwReaderConfig());
        LayerReaderConfig dltbConfig = LayerFactory.getReaderConfig(this.arg.getDltbReaderConfig());

        LayerReader lxdwReader = LayerFactory.getReader(this.ss, lxdwConfig);
        LayerReader xzdwReader = LayerFactory.getReader(this.ss, xzdwConfig);
        LayerReader dltbReader = LayerFactory.getReader(this.ss, dltbConfig);

        Layer lxdwLayer = lxdwReader.read();
        Layer xzdwLayer = xzdwReader.read();
        Layer dltbLayer = dltbReader.read();

        // 处理地类图斑层与现状地物层，连接字段为：zldwdm, tbbh=zltbbh
        JavaPairRDD<String, Feature> dltbLayer2 = dltbLayer.mapToPair(new PairFunction<Tuple2<String, Feature>, String, Feature>() {
            @Override
            public Tuple2<String, Feature> call(Tuple2<String, Feature> o) throws Exception {
                String[] kf = new String[2];
                kf[0] = o._2.getAttribute("ZLDWDM").toString();
                kf[1] = o._2.getAttribute("TBBH").toString();
                return new Tuple2<>(StringUtils.join(kf, "##"), o._2);
            }
        });

        JavaPairRDD<String, Feature> lxdwLayer2 = lxdwLayer.mapToPair(new PairFunction<Tuple2<String, Feature>, String, Feature>() {
            @Override
            public Tuple2<String, Feature> call(Tuple2<String, Feature> o) throws Exception {
                String[] kf = new String[2];
                kf[0] = o._2.getAttribute("ZLDWDM").toString();
                kf[1] = o._2.getAttribute("ZLTBBH").toString();
                return new Tuple2<>(StringUtils.join(kf, "##"), o._2);
            }
        });

        JavaPairRDD<String, Tuple2<Feature, Optional<Feature>>> tl1 = dltbLayer2.leftOuterJoin(lxdwLayer2);

        JavaPairRDD<String, Feature> l1 = tl1.mapToPair(new PairFunction<Tuple2<String, Tuple2<Feature, Optional<Feature>>>, String, Feature>() {
            @Override
            public Tuple2<String, Feature> call(Tuple2<String, Tuple2<Feature, Optional<Feature>>> in) throws Exception {
                Feature f = new Feature(in._2._1);
                if (!in._2._2.isPresent()) return new Tuple2<>(in._1, in._2._1);
                Feature lx = in._2._2.get();
                Field bsmF = new Field("lx_id");
                Field dlbmF = new Field("lx_dlbm");
                Field mjF = new Field("lx_mj");
                mjF.setType(Double.class);
                Field wktF = new Field("lx_wkt");
                f.addAttribute(bsmF, lx.getAttribute("BSM"));
                f.addAttribute(dlbmF, lx.getAttribute("DLBM"));
                f.addAttribute(mjF, lx.getAttribute("MJ"));
                f.addAttribute(wktF, lx.getAttribute("WKT"));

                String[] keys = new String[2];
                keys[0] = f.getAttribute("ZLDWDM").toString();
                keys[1] = f.getAttribute("TBBH").toString();
                return new Tuple2<>(StringUtils.join(keys, "##"), f);
            }
        });

        xzdwLayer.makeSureCached(StorageLevel.MEMORY_AND_DISK());
        JavaPairRDD<String, Feature> xzdwLayer1 = xzdwLayer.mapToPair(new PairFunction<Tuple2<String, Feature>, String, Feature>() {
            @Override
            public Tuple2<String, Feature> call(Tuple2<String, Feature> o) throws Exception {
                String[] kf = new String[2];
                kf[0] = o._2.getAttribute("KCTBDWDM1").toString();
                kf[1] = o._2.getAttribute("KCTBBH1").toString();
                return new Tuple2<>(StringUtils.join(kf, "##"), o._2);
            }
        });

        JavaPairRDD<String, Feature> xzdwLayer2 = xzdwLayer.mapToLayer(new PairFunction<Tuple2<String, Feature>, String, Feature>() {
            @Override
            public Tuple2<String, Feature> call(Tuple2<String, Feature> o) throws Exception {
                String[] kf = new String[2];
                kf[0] = o._2.getAttribute("KCTBDWDM2").toString();
                kf[1] = o._2.getAttribute("KCTBBH2").toString();
                return new Tuple2<>(StringUtils.join(kf, "##"), o._2);
            }
        });

        l1 = l1.leftOuterJoin(xzdwLayer1).mapToPair(new PairFunction<Tuple2<String, Tuple2<Feature, Optional<Feature>>>, String, Feature>() {
            @Override
            public Tuple2<String, Feature> call(Tuple2<String, Tuple2<Feature, Optional<Feature>>> in) throws Exception {
                Feature f = new Feature(in._2._1);
                if (!in._2._2.isPresent()) return new Tuple2<>(in._1, in._2._1);
                Feature xz = in._2._2.get();
                //处理现状单位代码的字段
                Field bsmF = new Field("xz_id");
                Field dlbmF = new Field("xz_dlbm");
                Field cdF = new Field("xz_cd");
                cdF.setType(Double.class);
                Field kdF = new Field("xz_kd");
                kdF.setType(Double.class);
                Field wktF = new Field("xz_wkt");
                Field kcblF = new Field("xz_kcbl");
                kcblF.setType(Double.class);
                f.addAttribute(bsmF, xz.getAttribute("BSM"));
                f.addAttribute(dlbmF, xz.getAttribute("DLBM"));
                f.addAttribute(cdF, xz.getAttribute("CD"));
                f.addAttribute(kdF, xz.getAttribute("KD"));
                f.addAttribute(wktF, xz.getAttribute("WKT"));
                f.addAttribute(kcblF, xz.getAttribute("KCBL"));
                return new Tuple2<>(in._1, f);
            }
        }).leftOuterJoin(xzdwLayer2).mapToPair(new PairFunction<Tuple2<String, Tuple2<Feature, Optional<Feature>>>, String, Feature>() {
            @Override
            public Tuple2<String, Feature> call(Tuple2<String, Tuple2<Feature, Optional<Feature>>> in) throws Exception {
                Feature f = new Feature(in._2._1);
                if (!in._2._2.isPresent()) return new Tuple2<>(in._1, in._2._1);
                Feature xz = in._2._2.get();
                //处理现状单位代码的字段
                Field bsmF = new Field("xz_id");
                Field dlbmF = new Field("xz_dlbm");
                Field cdF = new Field("xz_cd");
                cdF.setType(Double.class);
                Field kdF = new Field("xz_kd");
                kdF.setType(Double.class);
                Field wktF = new Field("xz_wkt");
                Field kcblF = new Field("xz_kcbl");
                kcblF.setType(Double.class);
                f.addAttribute(bsmF, xz.getAttribute("BSM"));
                f.addAttribute(dlbmF, xz.getAttribute("DLBM"));
                f.addAttribute(cdF, xz.getAttribute("CD"));
                f.addAttribute(kdF, xz.getAttribute("KD"));
                f.addAttribute(wktF, xz.getAttribute("WKT"));
                f.addAttribute(kcblF, xz.getAttribute("KCBL"));
                return new Tuple2<>(in._1, f);
            }
        });

        l1.saveAsTextFile("C:\\Users\\Hu\\Desktop\\23测试\\result");

        // TODO 写出
    }


    public static void main(String[] args) throws Exception {
        LandFlowPreProcess preProcess = new LandFlowPreProcess(SparkSessionType.LOCAL, args);
        preProcess.exec();
    }

}
