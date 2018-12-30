package train;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

public class Preprocess {

    /**
     * 有效像素颜色值
     */
    private static final int TARGET_COLOR = Color.BLACK.getRGB();
    /**
     * 无效像素颜色值
     */
    private static final int USELESS_COLOR = Color.WHITE.getRGB();

    public Preprocess() {

    }

    public static void main(String[] args) {
        System.out.println("---begin---");

        long start = System.currentTimeMillis();
        Preprocess model = new Preprocess();
        model.run();
        long end = System.currentTimeMillis();

        System.out.println("耗时：" + (end - start));
        System.out.println("---end----");
    }

    private void run() {
        File dir = new File("download");
        //只列出jpg
        File[] files = dir.listFiles(new FilenameFilter() {

            public boolean isJpg(String file) {
                if (file.toLowerCase().endsWith(".jpg")) {
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public boolean accept(File dir, String name) {
                // TODO Auto-generated method stub
                return isJpg(name);
            }
        });

        for (File file : files) {
            try {
                BufferedImage img = ImageIO.read(file);
                BufferedImage binaryImg = getBinaryImage(img);
                ImageIO.write(binaryImg, "JPG", new File("1_gray/" + file.getName()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 二值化
     *
     * @param sourceImage
     * @return 二值化之后的图像
     */
    public BufferedImage getBinaryImage(BufferedImage sourceImage) {
        double Wr = 0.299;
        double Wg = 0.587;
        double Wb = 0.114;

        int width = sourceImage.getWidth();
        int height = sourceImage.getHeight();
        int[][] gray = new int[width][height];

        //灰度化
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Color color = new Color(sourceImage.getRGB(x, y));
                int rgb = (int) ((color.getRed() * Wr + color.getGreen() * Wg + color.getBlue() * Wb) / 3);
                gray[x][y] = rgb;
            }
        }

        BufferedImage binaryBufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        //二值化
        int threshold = getOstu(gray, width, height);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (gray[x][y] > threshold) {
                    int max = new Color(255, 255, 255).getRGB();
                    gray[x][y] = max;
                } else {
                    int min = new Color(0, 0, 0).getRGB();
                    gray[x][y] = min;
                }

                binaryBufferedImage.setRGB(x, y, gray[x][y]);
            }
        }

        return denoise(binaryBufferedImage);
    }

    /**
     * 去噪
     *
     * @param img 图形验证码文件
     * @return
     */
    private BufferedImage denoise(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                if (x > 1 && x < width - 1 && y > 8 && y < height - 3
                        && isTarget(img.getRGB(x, y))) {
                    img.setRGB(x, y, TARGET_COLOR);
                } else {
                    img.setRGB(x, y, USELESS_COLOR);
                }
            }
        }

        for (int x = 1; x < width - 1; ++x) {
            for (int y = 8; y < height - 3; ++y) {
                if (img.getRGB(x, y) == TARGET_COLOR) {
                    int shotNum = 0;
                    for (int i = 0; i < 9; ++i) {
                        shotNum += img.getRGB(x - 1 + (i % 3), y - 1 + (i / 3)) == TARGET_COLOR ? 1 : 0;
                    }
                    if (shotNum <= 3) {
                        img.setRGB(x, y, USELESS_COLOR);
                    }
                }
            }
        }

        return img;
    }

    /**
     * 目标像素判断
     * <br />（基于明度）
     *
     * @param colorInt
     * @return
     */
    private boolean isTarget(int colorInt) {
        Color color = new Color(colorInt);
        float[] hsb = new float[3];
        Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);
        return hsb[2] < 0.7f; // b小于0.7
    }

    /**
     * 获得二值化图像
     * 最大类间方差法
     *
     * @param gray
     * @param width
     * @param height
     */
    private int getOstu(int[][] gray, int width, int height) {
        int grayLevel = 256;
        int[] pixelNum = new int[grayLevel];
        //计算所有色阶的直方图
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int color = gray[x][y];
                pixelNum[color]++;
            }
        }

        double sum = 0;
        int total = 0;
        for (int i = 0; i < grayLevel; i++) {
            sum += i * pixelNum[i]; //x*f(x)质量矩，也就是每个灰度的值乘以其点数（归一化后为概率），sum为其总和
            total += pixelNum[i]; //n为图象总的点数，归一化后就是累积概率
        }
        double sumB = 0;//前景色质量矩总和
        int threshold = 0;
        double wF = 0;//前景色权重
        double wB = 0;//背景色权重

        double maxFreq = -1.0;//最大类间方差

        for (int i = 0; i < grayLevel; i++) {
            wB += pixelNum[i]; //wB为在当前阈值背景图象的点数
            if (wB == 0) { //没有分出前景后景
                continue;
            }

            wF = total - wB; //wB为在当前阈值前景图象的点数
            if (wF == 0) {//全是前景图像，则可以直接break
                break;
            }

            sumB += (double) (i * pixelNum[i]);
            double meanB = sumB / wB;
            double meanF = (sum - sumB) / wF;
            //freq为类间方差
            double freq = (double) (wF) * (double) (wB) * (meanB - meanF) * (meanB - meanF);
            if (freq > maxFreq) {
                maxFreq = freq;
                threshold = i;
            }
        }

        return threshold;
    }


}
