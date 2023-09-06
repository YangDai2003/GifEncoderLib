package com.yangdai.gifencoderlib;

import java.io.IOException;
import java.io.OutputStream;

/**
 * LZW（Lempel-Ziv-Welch Encoding）算法又叫“串表压缩算法”就是通过建立一个字符串表，用较短的代码来表示较长的字符串来实现数据的无损压缩。
 * LZW压缩算法是 Unisys 的专利，有效期到 2003 年，所以现在对它的使用已经没有限制了。
 * LZW有三个重要对象：数据流（CharStream）、编码流（String Table）和编译表（String Table）。
 * （1）编码时，数据流是输入对象 （数据序列），编码流就是输出对象（经过压缩运算的编码数据）；
 * （2）解码时，编码流是输入对象，数据流是输出对象；而编译表是在编码和解码时都需要借助的对象。
 * @author 30415
 */
public class LZWEncoder {

    private static final int EOF = -1;
    private static final int BITS = 12;
    private static final int HSIZE = 5003; // 80% 占用率

    private final int imageWidth;
    private final int imageHeight;
    private final int initCodeSize;
    private int remainingPixels;
    private int currentPixel;
    private int numBits; // 编码位数
    private final int maxBits = BITS; // 用户可设置的最大位数
    private int maxCode; // 最大编码，根据 n_bits 计算得到
    private final int maxMaxCode = 1 << BITS; // 永远不该生成
    private final int hashSize = HSIZE; // 动态表大小
    private int freeCode = 0; // 第一个未使用的编码
    private int initialNumBits;
    private int clearCode;
    private int endOfFileCode;
    private int currentAccumulator = 0;
    private int currentNumBits = 0;
    private int accumulatorCount;

    private final int[] hashTable = new int[HSIZE];
    private final int[] codeTable = new int[HSIZE];
    private final int[] masks = {
            0x0000,
            0x0001,
            0x0003,
            0x0007,
            0x000F,
            0x001F,
            0x003F,
            0x007F,
            0x00FF,
            0x01FF,
            0x03FF,
            0x07FF,
            0x0FFF,
            0x1FFF,
            0x3FFF,
            0x7FFF,
            0xFFFF};

    private final byte[] pixelArray;
    private final byte[] accumulator = new byte[256];
    private boolean clearFlag = false;


    LZWEncoder(int width, int height, byte[] pixels, int colorDepth) {
        imageWidth = width;
        imageHeight = height;
        pixelArray = pixels;
        initCodeSize = Math.max(2, colorDepth);
    }

    /**
     * 字符添加到当前数据包的末尾，如果达到 254 个字符，则将数据包刷新到磁盘
     */
    void appendToAccumulator(byte c, OutputStream outs) throws IOException {
        accumulator[accumulatorCount++] = c;
        if (accumulatorCount >= 254) {
            flushAccumulator(outs);
        }
    }

    // 用于块压缩的表清除
    void clearBlock(OutputStream outs) throws IOException {
        clearHashTable(hashSize);
        freeCode = clearCode + 2;
        clearFlag = true;

        outputCode(clearCode, outs);
    }

    // 重置编码表
    void clearHashTable(int hsize) {
        for (int i = 0; i < hsize; ++i) {
            hashTable[i] = -1;
        }
    }

    void compress(int initBits, OutputStream outs) throws IOException {
        int currentCode;
        int i;
        int pixel;
        int currentEntry;
        int displacement;
        int hashSizeReg;
        int hashShift;

        // 设置全局变量：g_init_bits - 初始位数
        initialNumBits = initBits;

        // 设置必要的值
        clearFlag = false;
        numBits = initialNumBits;
        maxCode = getMaxCode(numBits);

        clearCode = 1 << (initBits - 1);
        endOfFileCode = clearCode + 1;
        freeCode = clearCode + 2;
        // 清空数据包
        accumulatorCount = 0;

        currentEntry = getNextPixel();

        hashShift = 0;
        for (currentCode = hashSize; currentCode < 65536; currentCode *= 2) {
            ++hashShift;
        }
        // 设置哈希码范围
        hashShift = 8 - hashShift;

        hashSizeReg = hashSize;
        // 清空哈希表
        clearHashTable(hashSizeReg);

        outputCode(clearCode, outs);

        outer_loop:
        while ((pixel = getNextPixel()) != EOF) {
            currentCode = (pixel << maxBits) + currentEntry;
            i = (pixel << hashShift) ^ currentEntry; // 异或哈希

            if (hashTable[i] == currentCode) {
                currentEntry = codeTable[i];
                continue;
            } else if (hashTable[i] >= 0) // 非空槽
            {
                displacement = hashSizeReg - i; // 二次哈希（G.Knott之后）
                if (i == 0) {
                    displacement = 1;
                }
                do {
                    if ((i -= displacement) < 0) {
                        i += hashSizeReg;
                    }

                    if (hashTable[i] == currentCode) {
                        currentEntry = codeTable[i];
                        continue outer_loop;
                    }
                } while (hashTable[i] >= 0);
            }
            outputCode(currentEntry, outs);
            currentEntry = pixel;
            if (freeCode < maxMaxCode) {
                codeTable[i] = freeCode++; // 编码 -> 哈希表
                hashTable[i] = currentCode;
            } else {
                clearBlock(outs);
            }
        }
        // 输出最后一个编码
        outputCode(currentEntry, outs);
        outputCode(endOfFileCode, outs);
    }

    void encode(OutputStream os) throws IOException {
        os.write(initCodeSize); // 写入 "初始编码大小" 字节

        remainingPixels = imageWidth * imageHeight; // 重置导航变量
        currentPixel = 0;

        compress(initCodeSize + 1, os); // 压缩并写入像素数据

        os.write(0); // 写入块终结符
    }

    // 刷新数据包到磁盘，并重置累加器
    void flushAccumulator(OutputStream outs) throws IOException {
        if (accumulatorCount > 0) {
            outs.write(accumulatorCount);
            outs.write(accumulator, 0, accumulatorCount);
            accumulatorCount = 0;
        }
    }

    final int getMaxCode(int nBits) {
        return (1 << nBits) - 1;
    }

    private int getNextPixel() {
        if (remainingPixels == 0) {
            return EOF;
        }

        --remainingPixels;

        byte pix = pixelArray[currentPixel++];

        return pix & 0xff;
    }

    void outputCode(int code, OutputStream outs) throws IOException {
        currentAccumulator &= masks[currentNumBits];

        if (currentNumBits > 0) {
            currentAccumulator |= (code << currentNumBits);
        } else {
            currentAccumulator = code;
        }

        currentNumBits += numBits;

        while (currentNumBits >= 8) {
            appendToAccumulator((byte) (currentAccumulator & 0xff), outs);
            currentAccumulator >>= 8;
            currentNumBits -= 8;
        }

        // 如果下一个条目对于编码大小来说太大，则增加编码大小（如果可能）
        if (freeCode > maxCode || clearFlag) {
            if (clearFlag) {
                maxCode = getMaxCode(numBits = initialNumBits);
                clearFlag = false;
            } else {
                ++numBits;
                if (numBits == maxBits) {
                    maxCode = maxMaxCode;
                } else {
                    maxCode = getMaxCode(numBits);
                }
            }
        }

        if (code == endOfFileCode) {
            // 在 EOF 时，写入剩余的缓冲区
            while (currentNumBits > 0) {
                appendToAccumulator((byte) (currentAccumulator & 0xff), outs);
                currentAccumulator >>= 8;
                currentNumBits -= 8;
            }

            flushAccumulator(outs);
        }
    }
}