# haikudepotserver-server-graphics

## Manual testing

### Render HVIF

```
curl -v -X POST --data=binary @x/y/z/file.hvif "http://localhost:8085/__gfx/hvif2png?sz=200 > /tmp/file.png
```

### Optimize PNG

```
curl -v -X POST --data=binary @x/y/z/file.png "http://localhost:8085/__gfx/optimize > /tmp/file.png
```

### Thumbnail

```
curl -v -X POST --data=binary @x/y/z/file.png "http://localhost:8085/__gfx/thumbnail?w=240&h=180 > /tmp/file.png
```