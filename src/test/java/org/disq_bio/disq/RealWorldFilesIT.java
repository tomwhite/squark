package org.disq_bio.disq;

import htsjdk.samtools.ValidationStringency;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test all files found in a pre-configured local directory. Useful for testing large real-world
 * files. The test carried out is a simple count of records, cross-checked against htsjdk, and
 * samtools/bcftools (if available on local system).
 */
@RunWith(JUnitParamsRunner.class)
public class RealWorldFilesIT extends BaseTest {

  private static final String REAL_WORLD_FILES_DIR_PROPERTY = "disq.test.real.world.files.dir";
  private static final String REAL_WORLD_FILES_REF_FILE_PROPERTY = "disq.test.real.world.files.ref";

  private Object[] parametersForTestReads() throws IOException {
    String dir = System.getProperty(REAL_WORLD_FILES_DIR_PROPERTY);
    if (dir == null) {
      return new Object[0];
    }
    String ref = System.getProperty(REAL_WORLD_FILES_REF_FILE_PROPERTY);
    return Files.walk(Paths.get(dir))
        .filter(p -> p.toString().matches(".*\\.(bam|cram|sam)$"))
        .map(p -> new Object[] {p, ref, 1024 * 1024, false})
        .toArray();
  }

  @Test
  @Parameters
  public void testReads(String inputFile, String cramReferenceFile, int splitSize, boolean useNio)
      throws Exception {
    String inputPath = new File(inputFile).toURI().toString();
    String refPath = new File(cramReferenceFile).toURI().toString();

    HtsjdkReadsRddStorage htsjdkReadsRddStorage =
        HtsjdkReadsRddStorage.makeDefault(jsc)
            .splitSize(splitSize)
            .useNio(useNio)
            .validationStringency(ValidationStringency.SILENT)
            .referenceSourcePath(refPath);

    HtsjdkReadsRdd htsjdkReadsRdd = htsjdkReadsRddStorage.read(inputPath);

    // read the file using htsjdk to get expected number of reads, then count the number in the RDD
    int expectedCount =
        AnySamTestUtil.countReads(inputPath, refPath, null, ValidationStringency.SILENT);
    Assert.assertEquals(expectedCount, htsjdkReadsRdd.getReads().count());

    if (SamtoolsTestUtil.isSamtoolsAvailable()) {
      Assert.assertEquals(expectedCount, SamtoolsTestUtil.countReads(inputPath, refPath));
    }
  }

  private Object[] parametersForTestVariants() throws IOException {
    String dir = System.getProperty(REAL_WORLD_FILES_DIR_PROPERTY);
    if (dir == null) {
      return new Object[0];
    }
    return Files.walk(Paths.get(dir))
        .filter(p -> p.toString().matches(".*\\.vcf(\\.gz|\\.bgz)?$"))
        .map(p -> new Object[] {p, 1024 * 1024})
        .toArray();
  }

  @Test
  @Parameters
  public void testVariants(String inputFile, int splitSize) throws IOException {
    String inputPath = new File(inputFile).toURI().toString();

    HtsjdkVariantsRddStorage htsjdkVariantsRddStorage =
        HtsjdkVariantsRddStorage.makeDefault(jsc).splitSize(splitSize);

    HtsjdkVariantsRdd htsjdkVariantsRdd = htsjdkVariantsRddStorage.read(inputPath);

    // read the file using htsjdk to get expected number of variants, then count the number in the
    // RDD
    int expectedCount = VcfTestUtil.countVariants(inputPath);
    Assert.assertEquals(expectedCount, htsjdkVariantsRdd.getVariants().count());

    if (BcftoolsTestUtil.isBcftoolsAvailable()) {
      Assert.assertEquals(expectedCount, BcftoolsTestUtil.countVariants(inputPath));
    }
  }
}
