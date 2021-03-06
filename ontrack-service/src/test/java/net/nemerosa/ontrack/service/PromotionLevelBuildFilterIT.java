package net.nemerosa.ontrack.service;

import net.nemerosa.ontrack.model.buildfilter.BuildFilterService;
import net.nemerosa.ontrack.model.structure.Build;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@SuppressWarnings("Duplicates")
public class PromotionLevelBuildFilterIT extends AbstractBuildFilterIT {

    @Autowired
    private BuildFilterService buildFilterService;

    /**
     * Tests the following sequence:
     * <p>
     * <pre>
     *     1
     *     2
     *     3
     *     4 --> COPPER
     *     5 --> BRONZE
     * </pre>
     */
    @Test
    public void distinct_sorted_promotion_levels() throws Exception {
        // Builds
        build(1);
        build(2);
        build(3);
        build(4).withPromotion(copper);
        build(5).withPromotion(bronze);
        // Filter
        List<Build> builds = asUserWithView(branch).call(() -> buildFilterService.lastPromotedBuildsFilterData().filterBranchBuilds(branch));
        // Checks the list
        checkList(builds, 5, 4);
    }

    /**
     * Tests the following sequence:
     * <p>
     * <pre>
     *     1
     *     2
     *     3 --> COPPER
     *     4
     *     5 --> BRONZE
     * </pre>
     */
    @Test
    public void distinct_separated_promotion_levels() throws Exception {
        // Builds
        build(1);
        build(2);
        build(3).withPromotion(copper);
        build(4);
        build(5).withPromotion(bronze);
        // Filter
        List<Build> builds = asUserWithView(branch).call(() -> buildFilterService.lastPromotedBuildsFilterData().filterBranchBuilds(branch));
        // Checks the list
        checkList(builds, 5, 3);
    }

    /**
     * Tests the following sequence:
     * <p>
     * <pre>
     *     1
     *     2
     *     3
     *     4 --> BRONZE
     *     5 --> COPPER
     * </pre>
     */
    @Test
    public void distinct_inverted_promotion_levels() throws Exception {
        // Builds
        build(1);
        build(2);
        build(3);
        build(4).withPromotion(bronze);
        build(5).withPromotion(copper);
        // Filter
        List<Build> builds = asUserWithView(branch).call(() -> buildFilterService.lastPromotedBuildsFilterData().filterBranchBuilds(branch));
        // Checks the list
        checkList(builds, 5, 4);
    }

    /**
     * Tests the following sequence:
     * <p>
     * <pre>
     *     1
     *     2
     *     3
     *     4 --> BRONZE
     *     5 --> COPPER, BRONZE
     * </pre>
     */
    @Test
    public void grouped_promotion_levels() throws Exception {
        // Builds
        build(1);
        build(2);
        build(3);
        build(4).withPromotion(bronze);
        build(5).withPromotion(copper).withPromotion(bronze);
        // Filter
        List<Build> builds = asUserWithView(branch).call(() -> buildFilterService.lastPromotedBuildsFilterData().filterBranchBuilds(branch));
        // Checks the list
        checkList(builds, 5);
    }

    /**
     * Tests the following sequence:
     * <p>
     * <pre>
     *     1
     *     2
     *     3
     *     4 --> COPPER, BRONZE
     *     5 --> BRONZE
     * </pre>
     */
    @Test
    public void grouped_promotion_levels_and_one_after() throws Exception {
        // Builds
        build(1);
        build(2);
        build(3);
        build(4).withPromotion(copper).withPromotion(bronze);
        build(5).withPromotion(bronze);
        // Filter
        List<Build> builds = asUserWithView(branch).call(() -> buildFilterService.lastPromotedBuildsFilterData().filterBranchBuilds(branch));
        // Checks the list
        checkList(builds, 5, 4);
    }

    /**
     * Tests the following sequence:
     * <p>
     * <pre>
     *     1
     *     2
     *     3
     *     4 --> COPPER, BRONZE
     *     5 --> BRONZE
     * </pre>
     */
    @Test
    public void grouped_promotion_levels_and_one_other_after() throws Exception {
        // Builds
        build(1);
        build(2);
        build(3);
        build(4).withPromotion(copper).withPromotion(bronze);
        build(5).withPromotion(gold);
        // Filter
        List<Build> builds = asUserWithView(branch).call(() -> buildFilterService.lastPromotedBuildsFilterData().filterBranchBuilds(branch));
        // Checks the list
        checkList(builds, 5, 4);
    }

}