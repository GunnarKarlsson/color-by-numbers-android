This is a color by number app.

The user will be able to:
+ select an image in a grid.
+ the image appears in black and white. a color palette with colors appears under the picture.
+ the colors have numbers. the areas in the image has numbers. 
+ the user selects a color and taps an area with that number.
+ the area's color changes to that color.
+ when all areas are colored the image is completed by the user.

+ images are saved as .cbn files, see projectinfo dir for examples
+ palettes are saved as .cbnpalette files, see projectinfo dir for examples.

App flow:
+ Grid with images - ImageGridActivity
+ Click an image and go into ColoringActivity

The .cbn images are created in an editor. in this editor, written in Rust, we've already tested the logic for how to render, edit and save images.

See PROJECT_DESCRIPTION.md and rust_implementation_snippets.md for details on implementation.

Plan

1. Create ColoringActivity that loads hardcoded topology_new_1.cbn and its palette. Make it show on the screen

2. Make it editable by the user - ie the game logic

3. Save progress

4. Create the ImageGridAcitivyt with thumbnails of images with full coloring.