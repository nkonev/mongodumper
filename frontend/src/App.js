import React, {useState, useEffect} from 'react';
import logo from './logo.svg';
import './App.css';
import {SERVER_URL} from "./config";
import axios from 'axios'
import Modal from '@material-ui/core/Modal';
import {makeStyles} from '@material-ui/core/styles';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemText from '@material-ui/core/ListItemText';
import Button from '@material-ui/core/Button';
import Grid from '@material-ui/core/Grid';
import TextField from '@material-ui/core/TextField';
import Fab from '@material-ui/core/Fab';
import AddIcon from '@material-ui/icons/Add';
import Fade from '@material-ui/core/Fade';

const useStyles = makeStyles(theme => ({
    root: {
        width: '100%',
        backgroundColor: theme.palette.background.paper,
    },
    fabButton: {
        position: 'absolute',
        zIndex: 1,
        bottom: 30,
        right: 30,
        margin: '0 auto',
    },
    paper: {
        position: 'absolute',
        width: 400,
        backgroundColor: theme.palette.background.paper,
        border: '2px solid #000',
        boxShadow: theme.shadows[5],
        padding: theme.spacing(2),
    },
}));

function getModalStyle() {
    const top = 50;
    const left = 50;

    return {
        top: `${top}%`,
        left: `${left}%`,
        transform: `translate(-${top}%, -${left}%)`,
    };
}

function App() {
    // state
    const [connections, setConnections] = useState([]);

    const fetchData = () => {
        console.log("before get");
        axios.get(`${SERVER_URL}/db`)
            .then(message => {
                const m = message.data;
                setConnections(m);
            });
    };

    useEffect(() => {
        fetchData();
    }, []);

    const classes = useStyles();

    const onDelete = id => {
        console.log("Deleting", id);
        axios.delete(`${SERVER_URL}/db/${id}`)
            .then(() => {
                fetchData();
            });
    };

    const onSave = (c) => {
        (c.id ? axios.put(`${SERVER_URL}/db`, c) : axios.post(`${SERVER_URL}/db`, c))
            .then(() => {
                fetchData();
                setOpen(false);
            });
    };

    // getModalStyle is not a pure function, we roll the style only on the first render
    const [modalStyle] = React.useState(getModalStyle);
    const [open, setOpen] = React.useState(false);
    const [editDto, setEditDto] = React.useState({});

    const handleOpen = (c) => {
        console.log("Editing modal", c.id);
        setEditDto(c);
        setOpen(true);
    };

    const handleClose = () => {
        setOpen(false);
    };

    const handleChangeName = event => {
        setEditDto({...editDto, name: event.target.value});
    };

    const handleChangeConnectionUrl = event => {
        setEditDto({...editDto, connectionUrl: event.target.value});
    };


    return (
        <div className="App">
            <div className={classes.root}>
                <header className="App-header">
                    <img src={logo} className="App-logo" alt="logo"/>
                </header>
                <List component="nav" aria-label="secondary mailbox folders">
                    {connections.map((value, index) => {
                        return (
                            <ListItem key={value.id} button>

                                <Grid container spacing={1} direction="row">
                                    <Grid item xs>
                                        <ListItemText primary={value.name + ' ' + value.connectionUrl}/>
                                    </Grid>

                                    <Grid container item xs={2} direction="row"
                                          justify="flex-end"
                                          alignItems="center" spacing={1}>
                                        <Grid item>
                                            <Button variant="contained" color="primary"
                                                    onClick={() => handleOpen(value)}>
                                                Edit
                                            </Button>
                                        </Grid>
                                        <Grid item>
                                            <Button variant="contained" color="secondary"
                                                    onClick={() => onDelete(value.id)}>
                                                Delete
                                            </Button>
                                        </Grid>
                                    </Grid>
                                </Grid>
                            </ListItem>
                        )
                    })}
                </List>

                <Fab color="primary" aria-label="add" className={classes.fabButton} onClick={() => handleOpen({name: '', connectionUrl: ''})}>
                    <AddIcon />
                </Fab>
            </div>

            <Modal
                aria-labelledby="simple-modal-title"
                aria-describedby="simple-modal-description"
                open={open}
                onClose={handleClose}
            >
                <Fade in={open}>
                <div style={modalStyle} className={classes.paper}>

                    <Grid container
                          direction="column"
                          justify="center"
                          alignItems="stretch"
                          spacing={2}>
                        <Grid item>
                            <span>{editDto.id ? 'Update connection' : 'Create connection'}</span>
                        </Grid>
                        <Grid item container spacing={1} direction="column" justify="center"
                              alignItems="stretch">
                            <Grid item>
                                <TextField id="outlined-basic" label="Name" variant="outlined" fullWidth value={editDto.name} onChange={handleChangeName}/>
                            </Grid>
                            <Grid item>
                                <TextField id="outlined-basic" label="Connection URL" variant="outlined" fullWidth value={editDto.connectionUrl} onChange={handleChangeConnectionUrl}/>
                            </Grid>

                        </Grid>
                        <Grid item container spacing={1}>
                            <Grid item>
                            <Button variant="contained" color="primary" onClick={() => onSave(editDto)}>
                                Save
                            </Button>
                            </Grid>
                            <Grid item>
                            <Button variant="contained" color="secondary" onClick={handleClose}>
                                Cancel
                            </Button>
                            </Grid>
                        </Grid>
                    </Grid>
                </div>
                </Fade>
            </Modal>
        </div>
    );
}

export default (App);