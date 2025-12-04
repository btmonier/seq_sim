package net.maizegenetics.commands

import net.maizegenetics.net.maizegenetics.commands.MutateAssemblies
import net.maizegenetics.net.maizegenetics.commands.Position
import net.maizegenetics.net.maizegenetics.commands.SimpleVariant
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MutateAssembliesTest {

    @Test
    fun splitRefBlockTest() {
        val mutateAssemblies = MutateAssemblies()

        //Test non overlapping variants
        val variantToSplit = SimpleVariant(Position("chr1", 100), Position("chr1", 200), "A", "<NON_REF>")

        val nonOverlappingVariant = SimpleVariant(Position("chr1", 250), Position("chr1", 250), "C", "G")

        assertFailsWith<IllegalArgumentException> {
            mutateAssemblies.splitRefBlock(variantToSplit, nonOverlappingVariant)
        }

        //check partially overlapping variants
        val partialOverlapLeft = SimpleVariant(Position("chr1", 50), Position("chr1", 150), "T", "G")
        assertFailsWith<IllegalArgumentException> {
            mutateAssemblies.splitRefBlock(variantToSplit, partialOverlapLeft)
        }

        val partialOverlapRight = SimpleVariant(Position("chr1", 150), Position("chr1", 250), "T", "G")
        assertFailsWith<IllegalArgumentException> {
            mutateAssemblies.splitRefBlock(variantToSplit, partialOverlapRight)
        }

        val variantToAdd = SimpleVariant(Position("chr1", 150), Position("chr1", 150), "T", "G")
        val splitVariants = mutateAssemblies.splitRefBlock(variantToSplit, variantToAdd)
        assert(splitVariants.size == 3)
        assert(splitVariants[0].refStart == Position("chr1", 100))
        assert(splitVariants[0].refEnd == Position("chr1", 149))
        assert(splitVariants[1] == variantToAdd)
        assert(splitVariants[2].refStart == Position("chr1", 151))
        assert(splitVariants[2].refEnd == Position("chr1", 200))

        //check boundaries
        val leftBoundaryVariant = SimpleVariant(Position("chr1", 100), Position("chr1", 100), "A", "G")
        val splitVariantsLeft = mutateAssemblies.splitRefBlock(variantToSplit, leftBoundaryVariant)
        assert(splitVariantsLeft.size == 2)
        assert(splitVariantsLeft[0] == leftBoundaryVariant)
        assert(splitVariantsLeft[1].refStart == Position("chr1", 101))
        assert(splitVariantsLeft[1].refEnd == Position("chr1", 200))

        val rightBoundaryVariant = SimpleVariant(Position("chr1", 200), Position("chr1", 200), "A", "G")
        val splitVariantsRight = mutateAssemblies.splitRefBlock(variantToSplit, rightBoundaryVariant)
        assert(splitVariantsRight.size == 2)
        assert(splitVariantsRight[0].refStart == Position("chr1", 100))
        assert(splitVariantsRight[0].refEnd == Position("chr1", 199))
        assert(splitVariantsRight[1] == rightBoundaryVariant)

    }
}