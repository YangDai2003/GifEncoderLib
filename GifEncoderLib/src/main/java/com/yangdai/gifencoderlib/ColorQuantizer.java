package com.yangdai.gifencoderlib;

/**
 * @author 30415
 */
public class ColorQuantizer {
    /**
     * 使用的颜色数量
     */
    protected static final int COLOR_COUNT = 256;

    /**
     * 四个接近500的质数 - 假设没有图像的长度太大
     */
    protected static final int PRIME_1 = 499;
    protected static final int PRIME_2 = 491;
    protected static final int PRIME_3 = 487;
    protected static final int PRIME_4 = 503;

    /**
     * 输入图像的最小大小
     */
    protected static final int MIN_PICTURE_BYTES = (3 * PRIME_4);

    protected static final int MAX_NET_POS = (COLOR_COUNT - 1);
    /**
     * 颜色值的偏移量
     */
    protected static final int NET_BIAS_SHIFT = 4;
    /**
     * 学习周期数
     */
    protected static final int NUM_CYCLES = 100;

    /**
     * 频率和偏差的定义
     */
    protected static final int INT_BIAS_SHIFT = 16;
    protected static final int INT_BIAS = (1 << INT_BIAS_SHIFT);
    /**
     * gamma = 1024
     */
    protected static final int GAMMA_SHIFT = 10;
    protected static final int GAMMA = (1 << GAMMA_SHIFT);
    protected static final int BETA_SHIFT = 10;
    /**
     * beta = 1/1024
     */
    protected static final int BETA = (INT_BIAS >> BETA_SHIFT);
    protected static final int BETA_GAMMA = (INT_BIAS << (GAMMA_SHIFT - BETA_SHIFT));

    /**
     * 减小半径因子的定义
     * 对于256个颜色，半径开始
     */
    protected static final int INIT_RADIUS = (COLOR_COUNT >> 3);
    /**
     * 在32.0处偏差6位
     */
    protected static final int RADIUS_BIAS_SHIFT = 6;
    protected static final int RADIUS_BIAS = (1 << RADIUS_BIAS_SHIFT);
    /**
     * 并且每个周期减小1/30的因子
     */
    protected static final int INIT_RADIUS_VALUE = (INIT_RADIUS * RADIUS_BIAS);
    protected static final int RADIUS_DEC = 30;

    /**
     * 减小alpha因子的定义
     * alpha从1.0开始
     */
    protected static final int ALPHA_BIAS_SHIFT = 10;
    protected static final int INITIAL_ALPHA = (1 << ALPHA_BIAS_SHIFT);
    /**
     * 偏差10位
     */
    protected int alphaDec;

    /**
     * RAD_BIAS 和 ALPHA_RAD_BIAS 用于计算 radPower
     */
    protected static final int RAD_BIAS_SHIFT = 8;
    protected static final int RAD_BIAS = (1 << RAD_BIAS_SHIFT);
    protected static final int ALPHA_RAD_BIAS_SHIFT = (ALPHA_BIAS_SHIFT + RAD_BIAS_SHIFT);
    protected static final int ALPHA_RAD_BIAS = (1 << ALPHA_RAD_BIAS_SHIFT);
    /**
     * 输入图像本身
     */
    protected byte[] inputImage;
    /**
     * = H*W*3
     */
    protected int pixelCount;
    /**
     * 采样因子 1..30
     */
    protected int sampleFactor;

    /**
     * BGRc
     */
    protected int[][] network;
    /**
     * 用于网络查找 - 实际上是256个
     */
    protected int[] networkIndex = new int[256];
    protected int[] bias = new int[COLOR_COUNT];
    /**
     * 用于学习的偏差和频率数组
     */
    protected int[] frequency = new int[COLOR_COUNT];
    /**
     * 预计算的 radPower
     */
    protected int[] radPower = new int[INIT_RADIUS];


    /**
     * 初始化网络范围为(0,0,0)到(255,255,255)并设置参数
     */
    public ColorQuantizer(byte[] input, int length, int sample) {

        int i;
        int[] p;

        inputImage = input;
        pixelCount = length;
        sampleFactor = sample;

        network = new int[COLOR_COUNT][];
        for (i = 0; i < COLOR_COUNT; i++) {
            network[i] = new int[4];
            p = network[i];
            p[0] = p[1] = p[2] = (i << (NET_BIAS_SHIFT + 8)) / COLOR_COUNT;
            frequency[i] = INT_BIAS / COLOR_COUNT;
            bias[i] = 0;
        }
    }

    public byte[] getColorMap() {
        byte[] map = new byte[3 * COLOR_COUNT];
        int[] index = new int[COLOR_COUNT];
        for (int i = 0; i < COLOR_COUNT; i++) {
            index[network[i][3]] = i;
        }
        int k = 0;
        for (int i = 0; i < COLOR_COUNT; i++) {
            int j = index[i];
            map[k++] = (byte) (network[j][0]);
            map[k++] = (byte) (network[j][1]);
            map[k++] = (byte) (network[j][2]);
        }
        return map;
    }

    /**
     * 对网络进行插入排序，并构建 netIndex[0..255]（在去偏后进行）
     */
    public void buildIndex() {

        int i, j, smallPos, smallVal;
        int[] p;
        int[] q;
        int previousCol, startPos;

        previousCol = 0;
        startPos = 0;
        for (i = 0; i < COLOR_COUNT; i++) {
            p = network[i];
            smallPos = i;
            smallVal = p[1];
            /* g上的索引 */
            /* 在i..COLOR_COUNT-1中找到最小值 */
            for (j = i + 1; j < COLOR_COUNT; j++) {
                q = network[j];
                if (q[1] < smallVal) {
                    smallPos = j;
                    smallVal = q[1];
                    /* g上的索引 */
                }
            }
            q = network[smallPos];
            /* 交换 p（i）和 q（smallPos）的条目 */
            if (i != smallPos) {
                j = q[0];
                q[0] = p[0];
                p[0] = j;
                j = q[1];
                q[1] = p[1];
                p[1] = j;
                j = q[2];
                q[2] = p[2];
                p[2] = j;
                j = q[3];
                q[3] = p[3];
                p[3] = j;
            }
            /* smallVal条目现在在位置 i */
            if (smallVal != previousCol) {
                networkIndex[previousCol] = (startPos + i) >> 1;
                for (j = previousCol + 1; j < smallVal; j++) {
                    networkIndex[j] = i;
                }
                previousCol = smallVal;
                startPos = i;
            }
        }
        networkIndex[previousCol] = (startPos + MAX_NET_POS) >> 1;
        for (j = previousCol + 1; j < 256; j++) {
            networkIndex[j] = MAX_NET_POS;
        }
    }

    /**
     * 主要学习循环
     */
    public void learn() {

        int i, j, b, g, r;
        int radius, rad, alpha, step, delta, samplePixels;
        byte[] p;
        int pix, lim;

        if (pixelCount < MIN_PICTURE_BYTES) {
            sampleFactor = 1;
        }
        alphaDec = 30 + ((sampleFactor - 1) / 3);
        p = inputImage;
        pix = 0;
        lim = pixelCount;
        samplePixels = pixelCount / (3 * sampleFactor);
        delta = samplePixels / NUM_CYCLES;
        alpha = INITIAL_ALPHA;
        radius = INIT_RADIUS_VALUE;

        rad = radius >> RADIUS_BIAS_SHIFT;
        for (i = 0; i < rad; i++) {
            radPower[i] =
                    alpha * (((rad * rad - i * i) * RAD_BIAS) / (rad * rad));
        }

        if (pixelCount < MIN_PICTURE_BYTES) {
            step = 3;
        } else if ((pixelCount % PRIME_1) != 0) {
            step = 3 * PRIME_1;
        } else {
            if ((pixelCount % PRIME_2) != 0) {
                step = 3 * PRIME_2;
            } else {
                if ((pixelCount % PRIME_3) != 0) {
                    step = 3 * PRIME_3;
                } else {
                    step = 3 * PRIME_4;
                }
            }
        }

        i = 0;
        while (i < samplePixels) {
            b = (p[pix] & 0xff) << NET_BIAS_SHIFT;
            g = (p[pix + 1] & 0xff) << NET_BIAS_SHIFT;
            r = (p[pix + 2] & 0xff) << NET_BIAS_SHIFT;
            j = findBiasedColor(b, g, r);

            moveSingleNeuron(alpha, j, b, g, r);
            if (rad != 0) {
                moveAdjacentNeurons(rad, j, b, g, r);
            }

            pix += step;
            if (pix >= lim) {
                pix -= pixelCount;
            }

            i++;
            if (delta == 0) {
                delta = 1;
            }
            if (i % delta == 0) {
                alpha -= alpha / alphaDec;
                radius -= radius / RADIUS_DEC;
                rad = radius >> RADIUS_BIAS_SHIFT;
                if (rad <= 1) {
                    rad = 0;
                }
                for (j = 0; j < rad; j++) {
                    radPower[j] =
                            alpha * (((rad * rad - j * j) * RAD_BIAS) / (rad * rad));
                }
            }
        }
    }

    /**
     * 搜索 BGR 值为 0..255（去偏后）并返回颜色索引
     */
    public int map(int b, int g, int r) {

        int i, j, dist, a, bestD;
        int[] p;
        int best;

        bestD = 1000;
        /* 最大距离为 256*3 */
        best = -1;
        i = networkIndex[g];
        /* g 上的索引 */
        j = i - 1;
        /* 从 netIndex[g] 开始向外查找 */

        while ((i < COLOR_COUNT) || (j >= 0)) {
            if (i < COLOR_COUNT) {
                p = network[i];
                dist = p[1] - g;
                if (dist >= bestD) {
                    i = COLOR_COUNT;
                    /* 停止迭代 */
                } else {
                    i++;
                    if (dist < 0) {
                        dist = -dist;
                    }
                    a = p[0] - b;
                    if (a < 0) {
                        a = -a;
                    }
                    dist += a;
                    if (dist < bestD) {
                        a = p[2] - r;
                        if (a < 0) {
                            a = -a;
                        }
                        dist += a;
                        if (dist < bestD) {
                            bestD = dist;
                            best = p[3];
                        }
                    }
                }
            }
            if (j >= 0) {
                p = network[j];
                dist = g - p[1];
                /* 在 g 上的索引 - 反向差异 */
                if (dist >= bestD) {
                    j = -1;
                    /* 停止迭代 */
                } else {
                    j--;
                    if (dist < 0) {
                        dist = -dist;
                    }
                    a = p[0] - b;
                    if (a < 0) {
                        a = -a;
                    }
                    dist += a;
                    if (dist < bestD) {
                        a = p[2] - r;
                        if (a < 0) {
                            a = -a;
                        }
                        dist += a;
                        if (dist < bestD) {
                            bestD = dist;
                            best = p[3];
                        }
                    }
                }
            }
        }
        return (best);
    }

    public byte[] process() {
        learn();
        unBiasNet();
        buildIndex();
        return getColorMap();
    }

    /**
     * 去偏网络，将值调整为0..255，并记录位置i以准备排序
     */
    public void unBiasNet() {
        @SuppressWarnings("unused")
        int i, j;

        for (i = 0; i < COLOR_COUNT; i++) {
            network[i][0] >>= NET_BIAS_SHIFT;
            network[i][1] >>= NET_BIAS_SHIFT;
            network[i][2] >>= NET_BIAS_SHIFT;
            network[i][3] = i;
        }
    }

    /**
     * 通过预计算的 alpha*(1-((i-j)^2/[r]^2)) 在 radPower[|i-j|] 中移动相邻的神经元
     */
    protected void moveAdjacentNeurons(int rad, int i, int b, int g, int r) {

        int j, k, lo, hi, a, m;
        int[] p;

        lo = i - rad;
        if (lo < -1) {
            lo = -1;
        }
        hi = i + rad;
        if (hi > COLOR_COUNT) {
            hi = COLOR_COUNT;
        }

        j = i + 1;
        k = i - 1;
        m = 1;
        while ((j < hi) || (k > lo)) {
            a = radPower[m++];
            if (j < hi) {
                p = network[j++];
                try {
                    p[0] -= (a * (p[0] - b)) / ALPHA_RAD_BIAS;
                    p[1] -= (a * (p[1] - g)) / ALPHA_RAD_BIAS;
                    p[2] -= (a * (p[2] - r)) / ALPHA_RAD_BIAS;
                } catch (Exception ignored) {
                }
            }
            if (k > lo) {
                p = network[k--];
                try {
                    p[0] -= (a * (p[0] - b)) / ALPHA_RAD_BIAS;
                    p[1] -= (a * (p[1] - g)) / ALPHA_RAD_BIAS;
                    p[2] -= (a * (p[2] - r)) / ALPHA_RAD_BIAS;
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 将神经元i朝着有偏差的(b,g,r)因子alpha移动
     */
    protected void moveSingleNeuron(int alpha, int i, int b, int g, int r) {

        /* alter hit neuron */
        int[] n = network[i];
        n[0] -= (alpha * (n[0] - b)) / INITIAL_ALPHA;
        n[1] -= (alpha * (n[1] - g)) / INITIAL_ALPHA;
        n[2] -= (alpha * (n[2] - r)) / INITIAL_ALPHA;
    }

    /**
     * 搜索有偏差的BGR值
     */
    protected int findBiasedColor(int b, int g, int r) {

        // 找到最接近的神经元（最小距离）并更新频率
        // 找到最佳神经元（最小距离-偏差）并返回位置
        // 对于频繁选择的神经元，freq[i]较高，bias[i]为负

        int i, dist, a, biasDist, betaFreq;
        int bestPos, bestBiasPos, bestDist, bestBiasDist;
        int[] n;

        bestDist = ~(1 << 31);
        bestBiasDist = bestDist;
        bestPos = -1;
        bestBiasPos = bestPos;

        for (i = 0; i < COLOR_COUNT; i++) {
            n = network[i];
            dist = n[0] - b;
            if (dist < 0) {
                dist = -dist;
            }
            a = n[1] - g;
            if (a < 0) {
                a = -a;
            }
            dist += a;
            a = n[2] - r;
            if (a < 0) {
                a = -a;
            }
            dist += a;
            if (dist < bestDist) {
                bestDist = dist;
                bestPos = i;
            }
            biasDist = dist - ((bias[i]) >> (INT_BIAS_SHIFT - NET_BIAS_SHIFT));
            if (biasDist < bestBiasDist) {
                bestBiasDist = biasDist;
                bestBiasPos = i;
            }
            betaFreq = (frequency[i] >> BETA_SHIFT);
            frequency[i] -= betaFreq;
            bias[i] += (betaFreq << GAMMA_SHIFT);
        }
        frequency[bestPos] += BETA;
        bias[bestPos] -= BETA_GAMMA;
        return (bestBiasPos);
    }
}