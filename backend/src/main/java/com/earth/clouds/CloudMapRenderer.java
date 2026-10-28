package com.earth.clouds;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * GMGSI 적외선 격자를 지구본용 등장방형 구름 이미지로 바꾼다.
 *
 * <p><b>투영.</b> GMGSI 격자는 메르카토르다(행 사이 위도 간격이 적도 0.072°에서 고위도 0.021°까지 줄고,
 * 메르카토르 좌표로는 일정하다. 실제 파일로 확인). 지구본 텍스처는 위도가 행에 비례하는 등장방형이라
 * 출력 행마다 위도 → 메르카토르 좌표 → 원본 행으로 거꾸로 찾아 쌍선형으로 읽는다.
 * 원본은 남북 약 72.7°까지만 있다. 그 밖은 0(구름 없음)으로 두고 프론트가 정적 구름으로 채운다.
 *
 * <p><b>값.</b> 0~255로 정규화된 밝기온도이고 차가울수록 크다. 구름 꼭대기는 차갑고 지표는 따뜻하다는
 * 것만으로 구름을 가른다. 고위도는 지표 자체가 차가워 구름처럼 보이므로 문턱을 위도에 따라 올린다.
 * 문턱값은 실제 파일을 지구본에 씌워 보며 정했다(태풍과 전선대는 남고 맑은 바다는 비도록).
 */
public final class CloudMapRenderer {

    /** 원본에서 행 묶음을 읽는다. 원본 전체(3000×4999 float, 60MB)를 한 번에 올리지 않기 위해서다. */
    @FunctionalInterface
    public interface RowReader {
        /** {@code [startRow, startRow + rowCount)} 행. 각 행의 길이는 원본 열 수. */
        float[][] read(int startRow, int rowCount);
    }

    /**
     * @param rows      원본 행 수
     * @param cols      원본 열 수. 0열이 경도 -180°이고 360°를 고르게 나눈다
     * @param northLat  0행의 위도
     * @param southLat  마지막 행의 위도
     */
    public record Source(int rows, int cols, double northLat, double southLat, RowReader reader) {
    }

    private static final int SLAB_ROWS = 256;
    private static final float JPEG_QUALITY = 0.85f;

    private CloudMapRenderer() {
    }

    public static byte[] renderJpeg(Source source, int width) {
        return encodeJpeg(render(source, width), width, width / 2);
    }

    /** 등장방형 흑백 픽셀(행 우선). 밝을수록 구름이 짙다. */
    static byte[] render(Source source, int width) {
        int height = width / 2;
        byte[] out = new byte[width * height];
        double mercNorth = mercator(source.northLat());
        double mercSouth = mercator(source.southLat());
        SlabCache slabs = new SlabCache(source);

        for (int r = 0; r < height; r++) {
            double lat = 90.0 - (r + 0.5) * 180.0 / height;
            if (lat > source.northLat() || lat < source.southLat()) {
                continue;
            }
            double sy = (mercNorth - mercator(lat)) / (mercNorth - mercSouth) * (source.rows() - 1);
            int y0 = (int) Math.floor(sy);
            int y1 = Math.min(y0 + 1, source.rows() - 1);
            double fy = sy - y0;
            float[] row0 = slabs.row(y0);
            float[] row1 = slabs.row(y1);
            double lo = cloudThreshold(lat);
            double hi = lo + 90.0;

            for (int c = 0; c < width; c++) {
                double lon = -180.0 + (c + 0.5) * 360.0 / width;
                double sx = (lon + 180.0) / 360.0 * source.cols();
                int x0 = Math.floorMod((int) Math.floor(sx), source.cols());
                // 경도 180°에서 끊기지 않게 마지막 열 다음은 0열로 잇는다.
                int x1 = (x0 + 1) % source.cols();
                double fx = sx - Math.floor(sx);
                double top = value(row0[x0]) * (1 - fx) + value(row0[x1]) * fx;
                double bottom = value(row1[x0]) * (1 - fx) + value(row1[x1]) * fx;
                double brightness = top * (1 - fy) + bottom * fy;

                double t = Math.clamp((brightness - lo) / (hi - lo), 0.0, 1.0);
                out[r * width + c] = (byte) Math.round(Math.pow(t, 0.8) * 255);
            }
        }
        return out;
    }

    /** 이 밝기 아래는 지표로 본다. 적도 112, 위도 70° 이상 150. */
    static double cloudThreshold(double lat) {
        double k = Math.min(Math.abs(lat) / 70.0, 1.0);
        return 112.0 + 38.0 * k * k;
    }

    // 결측값(-9999)은 구름 없음으로 본다.
    private static double value(float v) {
        return v < 0 ? 0 : v;
    }

    private static double mercator(double latDeg) {
        return Math.log(Math.tan(Math.PI / 4 + Math.toRadians(latDeg) / 2));
    }

    private static byte[] encodeJpeg(byte[] gray, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setDataElements(0, 0, width, height, gray);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /** 출력 행이 위에서 아래로 가면 원본 행도 단조 증가하므로, 묶음 하나만 들고 순서대로 넘긴다. */
    private static final class SlabCache {
        private final Source source;
        private int start = -1;
        private float[][] rows;

        SlabCache(Source source) {
            this.source = source;
        }

        float[] row(int index) {
            if (rows == null || index < start || index >= start + rows.length) {
                start = index;
                rows = source.reader().read(index, Math.min(SLAB_ROWS, source.rows() - index));
            }
            return rows[index - start];
        }
    }
}
