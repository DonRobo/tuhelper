import {StudyCourse, StudyData, StudyDataControllerService} from "./generated";
import React, {useEffect, useState} from "react";
import {Button, Form, ListGroup, ListGroupItem, Row} from "react-bootstrap";

export function CourseList(studyData: StudyData): JSX.Element {
    const [filter, setFilter] = useState<string>('');
    const [efforts, setEfforts] = useState<{ [key: string]: number }>({});
    const [onlyShowSet, setOnlyShowSet] = useState<boolean>(false);

    useEffect(() => {
        StudyDataControllerService.courseEfforts().then(efforts => {
            setEfforts(efforts);
        });
    }, []);

    const courses = studyData.segments
        .flatMap(segment => segment.moduleGroups)
        .flatMap(moduleGroup => moduleGroup.modules)
        .flatMap(module => module.courses)
        .filter((course, index, self) =>
            self.findIndex(c => c.id === course.id || c.actualName === course.actualName) === index
        );

    return <>
        <Form>
            <Form.Label htmlFor="filterInput">Filter</Form.Label>
            <Form.Control type="text" id="filterInput" value={filter} onChange={(event) => {
                setFilter(event.target.value);
            }}/>
            <Form.Check type="switch" label="Show only courses with effort set" checked={onlyShowSet}
                        onChange={(event) => {
                            setOnlyShowSet(event.target.checked);
                        }}/>
        </Form>
        <ListGroup style={{maxHeight: '80vh', overflowY: 'auto'}}>
            {
                courses
                    .filter(course => {
                        return course.actualName.toLowerCase().includes(filter.toLowerCase()) && (!onlyShowSet || (efforts[course.actualName] ?? 1.0) !== 1.0);
                    })
                    .sort((a, b) => a.actualName.localeCompare(b.actualName))
                    .map(course => <ListGroupItem key={course.id}>
                        <CourseListEntry course={course}
                                         initEffort={efforts[course.actualName] ?? 1.0}
                        />
                    </ListGroupItem>)
            }
        </ListGroup></>;
}

function CourseListEntry({course, initEffort}: { course: StudyCourse, initEffort: number }): JSX.Element {
    const [collapsed, setCollapsed] = useState(true);
    const [effort, setEffort] = useState(initEffort);
    const [effortSetting, setEffortSetting] = useState(initEffort);

    useEffect(() => {
        setEffort(initEffort);
    }, [initEffort]);

    const saveEffortSetting = () => {
        StudyDataControllerService.setEffort(course.actualName, effortSetting).then(() => {
            setEffort(effortSetting);
        });
    };

    if (collapsed) {
        return <div
            onClick={() => setCollapsed(!collapsed)}>{course.actualName}{effort === 1.0 ? '' : ` (${effort} multiplier set)`}</div>
    } else {
        return <div>
            <Row onClick={() => setCollapsed(!collapsed)}>{course.actualName}</Row>
            <Row>
                <Form.Label>Effort = {effortSetting}</Form.Label>
                <Form.Range step={0.05} min={0} max={10} value={effortSetting}
                            onChange={(event) => setEffortSetting(parseFloat(event.target.value))}>
                </Form.Range>
                {effort && effort !== effortSetting ? <Button onClick={saveEffortSetting}>Save</Button> : ''}
            </Row>
        </div>;
    }
}
