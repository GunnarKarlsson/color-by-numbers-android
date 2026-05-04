package network.bahn.colorbynumber.android.coloring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleJsonParserTest {
    @Test
    fun `parseDocument maps core puzzle fields`() {
        val json = """
            {
              "version": 1,
              "metadata": {
                "palette": [
                  {
                    "id": 2,
                    "label": "Blue",
                    "rgba": [74, 124, 232, 255]
                  }
                ],
                "palette_link": {
                  "palette_id": "palette-123",
                  "path": "palette_1.cbnpalette"
                }
              },
              "image_curve_data": {
                "bounds": [1200.0, 800.0],
                "regions": [
                  {
                    "id": 2,
                    "number": 2,
                    "number_position": [-210.0, -102.0],
                    "target_palette_id": 2
                  },
                  {
                    "id": 3,
                    "number": 4,
                    "number_position": [10.0, 20.0]
                  }
                ],
                "topology": {
                  "vertices": [
                    { "id": 1, "pos": [0.0, 0.0] },
                    { "id": 2, "pos": [10.0, 0.0] }
                  ],
                  "edges": [
                    { "id": 1, "start": 1, "end": 2 }
                  ],
                  "regions": [
                    {
                      "region_id": 2,
                      "outer": [
                        { "edge_id": 1, "reversed": false }
                      ],
                      "holes": [
                        [
                          { "edge_id": 1, "reversed": true }
                        ]
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val document = PuzzleJsonParser.parseDocument(json)

        assertEquals(1, document.version)
        assertEquals(PuzzlePoint(1200f, 800f), document.bounds)
        assertEquals(2, document.regions.size)
        assertEquals(2, document.regions.first().targetPaletteId)
        assertNull(document.regions.last().targetPaletteId)
        assertEquals("palette-123", document.paletteLink?.paletteId)
        assertEquals("palette_1.cbnpalette", document.paletteLink?.path)
        assertEquals(1, document.embeddedPalette.size)
        assertEquals(1, document.topology.edges.size)
        assertEquals(1, document.topology.regions.first().outer.size)
        assertEquals(1, document.topology.regions.first().holes.size)
    }

    @Test
    fun `parseDocument falls back to legacy boundary field`() {
        val json = """
            {
              "version": 1,
              "metadata": {},
              "image_curve_data": {
                "bounds": [100.0, 100.0],
                "regions": [
                  {
                    "id": 9,
                    "number": 1,
                    "number_position": [10.0, 10.0]
                  }
                ],
                "topology": {
                  "vertices": [
                    { "id": 1, "pos": [0.0, 0.0] },
                    { "id": 2, "pos": [10.0, 0.0] }
                  ],
                  "edges": [
                    { "id": 1, "start": 1, "end": 2 }
                  ],
                  "regions": [
                    {
                      "region_id": 9,
                      "boundary": [
                        { "edge_id": 1, "reversed": false }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val document = PuzzleJsonParser.parseDocument(json)

        assertEquals(1, document.topology.regions.single().outer.size)
        assertTrue(document.topology.regions.single().holes.isEmpty())
    }

    @Test
    fun `parsePalette maps standalone palette file`() {
        val json = """
            {
              "version": 1,
              "palette_id": "palette-123",
              "name": "palette_1",
              "colors": [
                {
                  "id": 1,
                  "label": "Red",
                  "rgba": [220, 64, 64, 255]
                },
                {
                  "id": 2,
                  "label": "Blue",
                  "rgba": [74, 124, 232, 255]
                }
              ]
            }
        """.trimIndent()

        val colors = PuzzleJsonParser.parsePalette(json)

        assertEquals(2, colors.size)
        assertEquals("Red", colors.first().label)
        assertEquals(255, colors.first().rgba[3])
        assertEquals(2, colors.last().id)
    }
}
