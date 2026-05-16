# Fix #6A: Dataset Annotation Protocol (Indian Road Context)

## 1. Primary Target Classes
To evolve SafeStrideAI for Indian roads, we need to extend the COCO/Pascal VOC set with these specific classes:

| Class ID | Name | Description | Priority |
|---|---|---|---|
| 101 | `auto-rickshaw` | Traditional 3-wheeled passenger vehicles (CNG/Petrol). | High |
| 102 | `e-rickshaw` | Electric 3-wheelers, usually slower but frequent in lanes. | High |
| 103 | `animal_stray` | Stray cows or dogs on the road. | Medium |
| 104 | `hand-cart` | Street vendors or porters pushing carts. | Medium |

## 2. Bounding Box Rules
* **Tightness**: Boxes must be within 2 pixels of the object boundary.
* **Occlusion**: If >70% of the object is hidden, do not annotate. If <70%, box the visible part but label as `occluded`.
* **Truncation**: If an object is partially out of frame, box the part inside the frame.

## 3. Scene Diversity Requirements
* **Time of Day**: 40% Daylight, 30% Dusk/Dawn, 30% Night.
* **Weather**: Must include 20% rainy/monsoon scenes (glare on roads).
* **Density**: 50% "Dense Traffic" (Delhi/Mumbai peak hours style), 30% "Residential Lanes".

## 4. Quality Control
* Every 100 images must be peer-reviewed by a Senior AI Engineer.
* Inter-Annotator Agreement (IoU) must be > 0.85.

## 5. Tooling
* Preferred Tool: **CVAT** or **LabelImg**.
* Export Format: **TFRecord** (for TFLite Model Maker) or **YOLO v8 format**.
